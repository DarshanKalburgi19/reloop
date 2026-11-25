package com.reloop.repair.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reloop.repair.exception.OrderServiceException;
import java.time.Duration;
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
 * REST implementation of {@link OrderServiceClient}.
 *
 * <ul>
 *   <li>Base URL: {@code reloop.services.order.base-url} (default http://localhost:8082).</li>
 *   <li>Service-to-service auth: header {@value #INTERNAL_KEY_HEADER} with {@code reloop.internal.api-key}
 *       (the same value in every ReLoop service).</li>
 *   <li>Timeouts: 2 s connect / 5 s read.</li>
 * </ul>
 */
@Component
public class HttpOrderServiceClient implements OrderServiceClient {

    public static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";
    public static final String DEV_DEFAULT_INTERNAL_KEY = "reloop-local-dev-internal-key-change-me";

    private static final Logger log = LoggerFactory.getLogger(HttpOrderServiceClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient restClient;

    @Autowired
    public HttpOrderServiceClient(
            @Value("${reloop.services.order.base-url:http://localhost:8082}") String baseUrl,
            @Value("${reloop.internal.api-key:" + DEV_DEFAULT_INTERNAL_KEY + "}") String internalApiKey) {
        this(RestClient.builder().requestFactory(timeoutFactory()), baseUrl, internalApiKey);
    }

    /** Test seam: lets tests bind a MockRestServiceServer to the builder. */
    public HttpOrderServiceClient(RestClient.Builder builder, String baseUrl, String internalApiKey) {
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
    public QuoteView createQuote(CreateQuoteCommand command) {
        return post("/api/internal/repair-quotes", command, "create the quote");
    }

    @Override
    public QuoteView approveQuote(Long quoteId, QuoteDecisionCommand command) {
        return post("/api/internal/repair-quotes/" + quoteId + "/approve", command, "approve the quote");
    }

    @Override
    public QuoteView rejectQuote(Long quoteId, QuoteDecisionCommand command) {
        return post("/api/internal/repair-quotes/" + quoteId + "/reject", command, "reject the quote");
    }

    private QuoteView post(String path, Object body, String action) {
        try {
            QuoteView view = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(QuoteView.class);
            if (view == null || view.id() == null) {
                throw new OrderServiceException(502, "Order service returned no quote", null);
            }
            return view;
        } catch (ResourceAccessException ex) {
            throw new OrderServiceException(503, "Order & Payment service is unavailable, so ReLoop could not " + action
                    + ". Nothing was changed; start order-service (port 8082) and try again.", ex);
        } catch (RestClientResponseException ex) {
            log.warn("order-service refused to {}: {} {}", action, ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().value() == 409) {
                throw new OrderServiceException(409, messageOf(ex, "Order service refused to " + action), ex);
            }
            throw new OrderServiceException(502,
                    "Order service could not " + action + " (HTTP " + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException ex) {
            throw new OrderServiceException(502, "Unexpected response from order service", ex);
        }
    }

    /** Uses order-service's JSON error {@code message} when present. */
    private static String messageOf(RestClientResponseException ex, String fallback) {
        try {
            JsonNode message = JSON.readTree(ex.getResponseBodyAsString()).get("message");
            return message != null && message.isTextual() ? message.asText() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
