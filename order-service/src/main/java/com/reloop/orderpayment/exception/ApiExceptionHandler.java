package com.reloop.orderpayment.exception;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * JSON errors for order-service: {@code {timestamp, status, error, message, path[, fieldErrors][, ...details]}}.
 * 401/403 for missing tokens or wrong roles are written by the security chain.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.isBindingFailure()
                    ? "invalid value '" + fe.getRejectedValue() + "'" : fe.getDefaultMessage());
        }
        return badRequest(fields, req);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> constraints(ConstraintViolationException ex, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            fields.putIfAbsent(v.getPropertyPath().toString(), v.getMessage());
        }
        return badRequest(fields, req);
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
        return badRequest(fields, req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> typeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return badRequest(Map.of(ex.getName(), "invalid value '" + ex.getValue() + "'"), req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        if (ex.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            String message = mismatch instanceof InvalidFormatException invalid
                    ? "invalid value '" + invalid.getValue() + "'" : "invalid value";
            return badRequest(Map.of(jsonPath(mismatch), message), req);
        }
        return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, "Malformed JSON", req));
    }

    @ExceptionHandler(InvalidInternalKeyException.class)
    public ResponseEntity<Map<String, Object>> key(InvalidInternalKeyException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body(HttpStatus.UNAUTHORIZED, ex.getMessage(), req));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(NotFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(HttpStatus.NOT_FOUND, ex.getMessage(), req));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict(ConflictException ex, HttpServletRequest req) {
        Map<String, Object> body = body(HttpStatus.CONFLICT, ex.getMessage(), req);
        body.putAll(ex.getDetails());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body(HttpStatus.CONFLICT, "Conflicts with existing data; reload and try again", req));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> badRequest(BadRequestException ex, HttpServletRequest req) {
        return badRequest(ex.fieldErrors(), req);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> forbidden(ForbiddenException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body(HttpStatus.FORBIDDEN, ex.getMessage(), req));
    }

    @ExceptionHandler(UnprocessableException.class)
    public ResponseEntity<Map<String, Object>> unprocessable(UnprocessableException ex, HttpServletRequest req) {
        Map<String, Object> body = body(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req);
        body.putAll(ex.getDetails());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(ProductServiceException.class)
    public ResponseEntity<Map<String, Object>> productService(ProductServiceException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatus());
        return ResponseEntity.status(status).body(body(status, ex.getMessage(), req));
    }

    @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> missingHeader(org.springframework.web.bind.MissingRequestHeaderException ex,
                                                             HttpServletRequest req) {
        return badRequest(Map.of(ex.getHeaderName(), "header is required"), req);
    }

    @ExceptionHandler(RepairServiceException.class)
    public ResponseEntity<Map<String, Object>> repairService(RepairServiceException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatus());
        return ResponseEntity.status(status).body(body(status, ex.getMessage(), req));
    }

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

    private static ResponseEntity<Map<String, Object>> badRequest(Map<String, String> fields, HttpServletRequest req) {
        Map<String, Object> body = body(HttpStatus.BAD_REQUEST, "Request validation failed", req);
        body.put("fieldErrors", fields);
        return ResponseEntity.badRequest().body(body);
    }

    public static Map<String, Object> body(HttpStatus status, String message, HttpServletRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", req.getRequestURI());
        return body;
    }
}
