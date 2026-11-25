package com.reloop.repair.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * JSON errors for repair-service REST endpoints:
 * {@code {timestamp, status, error, message, path[, fieldErrors][, profileId]}}.
 *
 * <p>401/403 for missing tokens or wrong roles are written by the security chain (see SecurityConfig).</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        return badRequest("Request validation failed", fieldErrors(ex), req);
    }

    /** Query parameters bound to a criteria object ({@code @ModelAttribute}): bad types and constraint failures. */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, Object>> binding(BindException ex, HttpServletRequest req) {
        return badRequest("Request validation failed", fieldErrors(ex), req);
    }

    @ExceptionHandler(RequestValidationException.class)
    public ResponseEntity<Map<String, Object>> crossField(RequestValidationException ex, HttpServletRequest req) {
        return badRequest("Request validation failed", ex.getFieldErrors(), req);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> constraints(ConstraintViolationException ex, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            fields.putIfAbsent(v.getPropertyPath().toString(), v.getMessage());
        }
        return badRequest("Request validation failed", fields, req);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> methodValidation(HandlerMethodValidationException ex, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                fields.putIfAbsent(name != null ? name : "parameter", error.getDefaultMessage());
            }
        });
        return badRequest("Request validation failed", fields, req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> typeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return badRequest("Request validation failed", Map.of(ex.getName(), "invalid value '" + ex.getValue() + "'"), req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> illegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, ex.getMessage(), req));
    }

    /** Malformed JSON, or a value of the wrong type/unknown enum constant (reported per field when possible). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        if (ex.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            String field = jsonPath(mismatch);
            String message = mismatch instanceof InvalidFormatException invalid
                    ? "invalid value '" + invalid.getValue() + "'"
                    : "invalid value";
            return badRequest("Request validation failed", Map.of(field, message), req);
        }
        return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, "Malformed JSON", req));
    }

    @ExceptionHandler(InvalidInternalKeyException.class)
    public ResponseEntity<Map<String, Object>> key(InvalidInternalKeyException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body(HttpStatus.UNAUTHORIZED, ex.getMessage(), req));
    }

    @ExceptionHandler(ProfileOwnershipException.class)
    public ResponseEntity<Map<String, Object>> ownership(ProfileOwnershipException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body(HttpStatus.FORBIDDEN, ex.getMessage(), req));
    }

    @ExceptionHandler(WorkshopProfileExistsException.class)
    public ResponseEntity<Map<String, Object>> exists(WorkshopProfileExistsException ex, HttpServletRequest req) {
        Map<String, Object> body = body(HttpStatus.CONFLICT, ex.getMessage(), req);
        body.put("profileId", ex.getProfileId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler({ProfileAlreadyExistsException.class, ProfileStateException.class, DataIntegrityViolationException.class})
    public ResponseEntity<Map<String, Object>> conflict(RuntimeException ex, HttpServletRequest req) {
        String message = ex instanceof DataIntegrityViolationException ? "Profile conflicts with existing data" : ex.getMessage();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(HttpStatus.CONFLICT, message, req));
    }

    @ExceptionHandler(RepairStateException.class)
    public ResponseEntity<Map<String, Object>> repairState(RepairStateException ex, HttpServletRequest req) {
        Map<String, Object> body = body(HttpStatus.CONFLICT, ex.getMessage(), req);
        body.put("currentStatus", ex.getCurrentStatus());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler({RepairerUnavailableException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<Map<String, Object>> busy(RuntimeException ex, HttpServletRequest req) {
        String message = ex instanceof OptimisticLockingFailureException
                ? "This repair request was changed by someone else at the same time. Reload it and try again."
                : ex.getMessage();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(HttpStatus.CONFLICT, message, req));
    }

    @ExceptionHandler(OrderServiceException.class)
    public ResponseEntity<Map<String, Object>> orderService(OrderServiceException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatus());
        return ResponseEntity.status(status).body(body(status, ex.getMessage(), req));
    }

    @ExceptionHandler(RepairRequestNotFoundException.class)
    public ResponseEntity<Map<String, Object>> repairNotFound(RepairRequestNotFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(HttpStatus.NOT_FOUND, ex.getMessage(), req));
    }

    @ExceptionHandler(ProfileNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(ProfileNotFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(HttpStatus.NOT_FOUND, ex.getMessage(), req));
    }

    /* ───────────────────────────── helpers ───────────────────────────── */

    /** Field errors first (binding failures become "invalid value 'x'"), then class-level rules keyed by name. */
    private static Map<String, String> fieldErrors(BindException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            String message = fe.isBindingFailure()
                    ? "invalid value '" + fe.getRejectedValue() + "'"
                    : fe.getDefaultMessage();
            fields.putIfAbsent(fe.getField(), message);
        }
        for (ObjectError ge : ex.getBindingResult().getGlobalErrors()) {
            fields.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage());
        }
        return fields;
    }

    /** ["workingHours", 0, "day"] -> "workingHours[0].day" */
    private static String jsonPath(JsonMappingException ex) {
        StringBuilder sb = new StringBuilder();
        for (JsonMappingException.Reference ref : ex.getPath()) {
            if (ref.getFieldName() != null) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.isEmpty() ? "body" : sb.toString();
    }

    private static ResponseEntity<Map<String, Object>> badRequest(String message, Map<String, String> fields,
                                                                  HttpServletRequest req) {
        Map<String, Object> body = body(HttpStatus.BAD_REQUEST, message, req);
        body.put("fieldErrors", fields);
        return ResponseEntity.badRequest().body(body);
    }

    private static Map<String, Object> body(HttpStatus status, String message, HttpServletRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", req.getRequestURI());
        return body;
    }
}
