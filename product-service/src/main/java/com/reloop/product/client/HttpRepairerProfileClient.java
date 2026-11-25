package com.reloop.product.client;

import com.reloop.product.exception.RepairServiceException;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * REST implementation of {@link RepairerProfileClient}.
 *
 * <ul>
 *   <li>Base URL: {@code reloop.services.repair.base-url} (default http://localhost:8083).</li>
 *   <li>Service-to-service auth: header {@value #INTERNAL_KEY_HEADER} with
 *       {@code reloop.internal.api-key} (must match repair-service's value).</li>
 *   <li>Timeouts: 2 s connect / 5 s read so registration fails fast instead of hanging.</li>
 * </ul>
 */
@Component
public class HttpRepairerProfileClient implements RepairerProfileClient {

    public static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";
    public static final String DEV_DEFAULT_INTERNAL_KEY = "reloop-local-dev-internal-key-change-me";

    private static final Logger log = LoggerFactory.getLogger(HttpRepairerProfileClient.class);

    private final RestClient restClient;

    @Autowired
    public HttpRepairerProfileClient(
            @Value("${reloop.services.repair.base-url:http://localhost:8083}") String baseUrl,
            @Value("${reloop.internal.api-key:" + DEV_DEFAULT_INTERNAL_KEY + "}") String internalApiKey) {
        this(RestClient.builder().requestFactory(timeoutFactory()), baseUrl, internalApiKey);
    }

    /** Test seam: lets tests bind a MockRestServiceServer to the builder. */
    public HttpRepairerProfileClient(RestClient.Builder builder, String baseUrl, String internalApiKey) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(INTERNAL_KEY_HEADER, internalApiKey)
                .build();
    }

    private static SimpleClientHttpRequestFactory timeoutFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    @Override
    public RepairerProfileView createProfile(CreateRepairerProfileCommand command) {
        try {
            RepairerProfileView view = restClient.post()
                    .uri("/api/repairer-profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(command)
                    .retrieve()
                    .body(RepairerProfileView.class);
            if (view == null || view.id() == null) {
                throw new RepairServiceException(502, "repair-service returned no profile id", null);
            }
            return view;
        } catch (ResourceAccessException ex) {
            throw new RepairServiceException(503,
                    "Repair service is unavailable, so the repairer workshop profile could not be created. "
                            + "Start repair-service (port 8083) and try again.", ex);
        } catch (RestClientResponseException ex) {
            log.warn("repair-service rejected profile creation: {} {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new RepairServiceException(502,
                    "Repair service rejected the workshop profile (HTTP " + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException ex) {
            throw new RepairServiceException(502, "Unexpected response from repair service", ex);
        }
    }

    @Override
    public Optional<RepairerProfileView> findProfile(Long profileId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/api/repairer-profiles/{id}", profileId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(RepairerProfileView.class));
        } catch (RestClientException ex) {
            log.debug("Could not load repairer profile {}: {}", profileId, ex.getMessage());
            return Optional.empty();
        }
    }
}
