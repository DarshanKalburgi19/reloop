package com.reloop.orderpayment.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reloop.orderpayment.exception.ProductServiceException;
import com.reloop.orderpayment.security.InternalApiKeyGuard;
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
 * REST implementation of {@link ProductServiceClient}.
 * <ul>
 *   <li>Base URL: {@code reloop.services.product.base-url} (default http://localhost:8081).</li>
 *   <li>Auth: {@code X-Internal-Api-Key} = {@code reloop.internal.api-key}.</li>
 *   <li>Timeouts: 2 s connect / 5 s read.</li>
 *   <li>Errors: 404 / 403 / 409 are passed on with product-service's message; unreachable → 503; anything
 *       else → 502.</li>
 * </ul>
 */
@Component
public class HttpProductServiceClient implements ProductServiceClient {

    private static final Logger log = LoggerFactory.getLogger(HttpProductServiceClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient restClient;

    @Autowired
    public HttpProductServiceClient(
            @Value("${reloop.services.product.base-url:http://localhost:8081}") String baseUrl,
            @Value("${reloop.internal.api-key:reloop-local-dev-internal-key-change-me}") String internalApiKey) {
        this(RestClient.builder().requestFactory(timeoutFactory()), baseUrl, internalApiKey);
    }

    /** Test seam: lets tests bind a MockRestServiceServer to the builder. */
    public HttpProductServiceClient(RestClient.Builder builder, String baseUrl, String internalApiKey) {
        this.restClient = builder.baseUrl(baseUrl).defaultHeader(InternalApiKeyGuard.HEADER, internalApiKey).build();
    }

    private static SimpleClientHttpRequestFactory timeoutFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    @Override
    public ProductSale reserve(Long productId, Long buyerAccountId, String orderReference) {
        return call("reserve", productId, new ProductReservationCommand(buyerAccountId, orderReference, null, null),
                "Product could not be reserved", "Product service is unavailable; nothing was charged. Try again shortly");
    }

    @Override
    public ProductSale confirmSale(Long productId, Long buyerAccountId, String orderReference, String transactionId) {
        return call("confirm-sale", productId,
                new ProductReservationCommand(buyerAccountId, orderReference, transactionId, null),
                "Product service failed to record the sale; the payment was not taken",
                "Product service is unavailable; nothing was charged. Try again shortly");
    }

    @Override
    public ProductSale release(Long productId, String orderReference, String reason) {
        return call("release", productId, new ProductReservationCommand(null, orderReference, null, reason),
                "Product service failed to release the reservation",
                "Product service is unavailable; the reservation was not released. Try again shortly");
    }

    @Override
    public ProductSale recordRefund(Long productId, ProductSaleRefundCommand refund) {
        return call("sale-refund", productId, refund, "Product service failed to record the refund; the refund was not issued",
                "Product service is unavailable; the refund was not issued. Try again shortly");
    }

    private ProductSale call(String action, Long productId, Object body, String fallback, String unavailable) {
        try {
            return restClient.post()
                    .uri("/api/internal/products/{id}/" + action, productId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ProductSale.class);
        } catch (ResourceAccessException ex) {
            log.warn("product-service unreachable ({}): {}", action, ex.getMessage());
            throw new ProductServiceException(503, unavailable, ex);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404 || status == 403 || status == 409) {
                throw new ProductServiceException(status, messageOf(ex, fallback), ex);
            }
            log.warn("product-service answered {} to {}: {}", status, action, ex.getResponseBodyAsString());
            throw new ProductServiceException(502, fallback, ex);
        } catch (RestClientException ex) {
            log.warn("product-service call failed ({}): {}", action, ex.getMessage());
            throw new ProductServiceException(502, fallback, ex);
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
