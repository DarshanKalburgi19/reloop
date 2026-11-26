package com.reloop.orderpayment.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reloop.orderpayment.exception.RepairServiceException;
import com.reloop.orderpayment.security.InternalApiKeyGuard;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * REST implementation of {@link RepairServiceClient}.
 * <ul>
 *   <li>Base URL: {@code reloop.services.repair.base-url} (default http://localhost:8083).</li>
 *   <li>Auth: {@code X-Internal-Api-Key} = {@code reloop.internal.api-key} (same value in every service).</li>
 *   <li>Timeouts: 2 s connect / 5 s read.</li>
 * </ul>
 */
@Component
public class HttpRepairServiceClient implements RepairServiceClient {

    private static final Logger log = LoggerFactory.getLogger(HttpRepairServiceClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient restClient;

    @Autowired
    public HttpRepairServiceClient(
            @Value("${reloop.services.repair.base-url:http://localhost:8083}") String baseUrl,
            @Value("${reloop.internal.api-key:reloop-local-dev-internal-key-change-me}") String internalApiKey) {
        this(RestClient.builder().requestFactory(timeoutFactory()), baseUrl, internalApiKey);
    }

    /** Test seam: lets tests bind a MockRestServiceServer to the builder. */
    public HttpRepairServiceClient(RestClient.Builder builder, String baseUrl, String internalApiKey) {
        this.restClient = builder.baseUrl(baseUrl).defaultHeader(InternalApiKeyGuard.HEADER, internalApiKey).build();
    }

    private static SimpleClientHttpRequestFactory timeoutFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    @Override
    public void confirmPayment(Long repairRequestId, PaymentConfirmation confirmation) {
        post("/api/internal/repair-requests/{id}/payment-confirmation", repairRequestId, confirmation,
                "Repair service refused the payment",
                "Repair service is unavailable; the payment was not taken. Try again shortly",
                "Repair service failed to record the payment; the payment was not taken");
    }

    @Override
    public void reportPaymentEvent(Long repairRequestId, RepairPaymentEvent event) {
        boolean refund = RepairPaymentEvent.REFUNDED.equals(event.type())
                || RepairPaymentEvent.PARTIALLY_REFUNDED.equals(event.type());
        post("/api/internal/repair-requests/{id}/payment-events", repairRequestId, event,
                "Repair service refused the payment event",
                refund ? "Repair service is unavailable; the refund was not issued. Try again shortly"
                        : "Repair service is unavailable; the failed payment could not be reported",
                refund ? "Repair service failed to record the refund; the refund was not issued"
                        : "Repair service failed to record the failed payment");
    }

    private void post(String path, Long repairRequestId, Object body, String conflictFallback, String unavailable,
                      String failed) {
        try {
            restClient.post()
                    .uri(path, repairRequestId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (ResourceAccessException ex) {
            log.warn("repair-service unreachable: {}", ex.getMessage());
            throw new RepairServiceException(503, unavailable, ex);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                throw new RepairServiceException(409, messageOf(ex, conflictFallback), ex);
            }
            log.warn("repair-service answered {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new RepairServiceException(502, failed, ex);
        } catch (RestClientException ex) {
            log.warn("repair-service call failed: {}", ex.getMessage());
            throw new RepairServiceException(502, failed, ex);
        }
    }

    private static String messageOf(RestClientResponseException ex, String fallback) {
        try {
            JsonNode message = JSON.readTree(ex.getResponseBodyAsString()).get("message");
            return message != null && message.isTextual() ? message.asText() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
