package com.reloop.product.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.reloop.product.exception.RepairServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Unit tests of the HTTP contract with repair-service (no Spring context, no real server). */
class HttpRepairerProfileClientTest {

    private static final String PROFILE_JSON = """
        {"id":55,"accountExternalId":7,"businessName":"Fix-It","specialties":"Phones",
         "addressLine":"5 CG Road","city":"Ahmedabad","pincode":"380006","latitude":23.0,"longitude":72.5,
         "serviceHours":"Mon-Sat","acceptsInPerson":true,"acceptsPickupDrop":false,"acceptsShipping":true,
         "verificationStatus":"UNVERIFIED","status":"ACTIVE","someFutureField":"ignored"}
        """;

    private static CreateRepairerProfileCommand command() {
        return new CreateRepairerProfileCommand(7L, "Fix-It", "Phones", "9898989898", "5 CG Road", "Ahmedabad",
                "380006", 23.0, 72.5, "Mon-Sat", true, false, true);
    }

    @Test
    void createSendsInternalKeyAndJsonAndParsesTheProfile() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpRepairerProfileClient client = new HttpRepairerProfileClient(builder, "http://repair.test", "secret-key");

        server.expect(requestTo("http://repair.test/api/repairer-profiles"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "secret-key"))
                .andExpect(jsonPath("$.accountExternalId").value(7))
                .andExpect(jsonPath("$.acceptsShipping").value(true))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(PROFILE_JSON));

        RepairerProfileView view = client.createProfile(command());
        assertThat(view.id()).isEqualTo(55L);
        assertThat(view.verificationStatus()).isEqualTo("UNVERIFIED");
        assertThat(view.acceptsShipping()).isTrue();
        server.verify();
    }

    @Test
    void errorAnswerBecomes502() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpRepairerProfileClient client = new HttpRepairerProfileClient(builder, "http://repair.test", "k");
        server.expect(requestTo("http://repair.test/api/repairer-profiles"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.createProfile(command()))
                .isInstanceOf(RepairServiceException.class)
                .satisfies(ex -> assertThat(((RepairServiceException) ex).getStatus()).isEqualTo(502));
    }

    @Test
    void unreachableServiceBecomes503() {
        // port 1 is never listening -> connection refused
        HttpRepairerProfileClient client = new HttpRepairerProfileClient("http://127.0.0.1:1", "k");

        assertThatThrownBy(() -> client.createProfile(command()))
                .isInstanceOf(RepairServiceException.class)
                .satisfies(ex -> assertThat(((RepairServiceException) ex).getStatus()).isEqualTo(503));
        assertThat(client.findProfile(1L)).isEmpty();
    }

    @Test
    void missingProfileReadsAsEmpty() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpRepairerProfileClient client = new HttpRepairerProfileClient(builder, "http://repair.test", "k");
        server.expect(requestTo("http://repair.test/api/repairer-profiles/9")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.findProfile(9L)).isEmpty();
    }
}
