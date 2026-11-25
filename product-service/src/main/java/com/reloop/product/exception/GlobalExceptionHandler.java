package com.reloop.product.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps exceptions to {@link ApiError} JSON. Messages are generic for server errors so internal
 * details (SQL, stack traces, hashes) are never exposed.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Request validation failed", req, fieldErrors(ex));
    }

    /** Query-parameter binding (e.g. GET /api/products?category=NOPE) - same JSON shape. */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> binding(BindException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Request validation failed", req, fieldErrors(ex));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'", req,
                Map.of(ex.getName(), "invalid value"));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> methodValidation(HandlerMethodValidationException ex, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            String message = result.getResolvableErrors().isEmpty() ? "invalid value"
                    : result.getResolvableErrors().get(0).getDefaultMessage();
            fields.putIfAbsent(name == null ? "parameter" : name, message);
        });
        return build(HttpStatus.BAD_REQUEST, "Request validation failed", req, fields);
    }

    private static Map<String, String> fieldErrors(BindException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            String message = fe.isBindingFailure()
                    ? "invalid value '" + fe.getRejectedValue() + "'"
                    : fe.getDefaultMessage();
            fields.putIfAbsent(fe.getField(), message);
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> fields.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        return fields;
    }

    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ApiError> fieldValidation(FieldValidationException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Request validation failed", req, Map.of(ex.getField(), ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST,
                "Malformed JSON or invalid field value (check enum values such as repairMethods)", req, Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> mediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/json", req, Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> notFound(NoResourceFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "No endpoint " + req.getMethod() + " " + req.getRequestURI(), req, Map.of());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiError> productNotFound(ProductNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler({ListingOwnershipException.class, OwnProductPurchaseException.class})
    public ResponseEntity<ApiError> forbiddenBusinessRule(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler({ProductNotAvailableException.class, ListingStateException.class})
    public ResponseEntity<ApiError> listingConflict(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req, Map.of());
    }

    /** Another purchase holds the row lock too long (H2 lock timeout 50200) or another concurrency conflict. */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ApiError> lockConflict(ConcurrencyFailureException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "This product is being purchased by someone else; please try again", req, Map.of());
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiError> duplicateEmail(EmailAlreadyRegisteredException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req, Map.of());
    }

    /** Race-condition safety net: uk_accounts_email fired although the pre-check passed. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        String detail = String.valueOf(ex.getMostSpecificCause().getMessage()).toUpperCase();
        if (detail.contains("UK_ACCOUNTS_EMAIL")) {
            return build(HttpStatus.CONFLICT, "An account with this email already exists", req, Map.of());
        }
        log.warn("Data integrity violation on {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "Request conflicts with existing data", req, Map.of());
    }

    @ExceptionHandler({InvalidCredentialsException.class, AccountNotFoundException.class})
    public ResponseEntity<ApiError> unauthorized(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> authentication(AuthenticationException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "Authentication required", req, Map.of());
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ApiError> notActive(AccountNotActiveException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), req, Map.of());
    }

    /** Raised by {@code @PreAuthorize} inside controllers - keep it a 403, not a 500. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> accessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "Your account role is not allowed to use this endpoint", req, Map.of());
    }

    @ExceptionHandler(RepairServiceException.class)
    public ResponseEntity<ApiError> repairService(RepairServiceException ex, HttpServletRequest req) {
        return build(HttpStatus.valueOf(ex.getStatus()), ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", req, Map.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest req,
                                           Map<String, String> fields) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message,
                req.getRequestURI(), fields);
        return ResponseEntity.status(status).body(body);
    }
}
