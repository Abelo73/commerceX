package com.commercex.common.web;

import com.commercex.common.error.BusinessException;
import com.commercex.common.error.ErrorCode;
import com.commercex.common.error.FieldViolation;
import com.commercex.common.error.ValidationException;
import com.commercex.common.ratelimit.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates every exception into an RFC 7807 problem response with {@code code} and
 * {@code correlationId} extensions. Unexpected errors never leak internals to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ProblemDetail handleBusiness(BusinessException ex, HttpServletRequest request) {
        ProblemDetail body = problem(ex.code(), ex.getMessage(), request.getRequestURI());
        if (!ex.details().isEmpty()) {
            body.setProperty("details", ex.details());
        }
        return body;
    }

    @ExceptionHandler(ValidationException.class)
    ProblemDetail handleValidation(ValidationException ex, HttpServletRequest request) {
        ProblemDetail body = problem(ex.code(), ex.getMessage(), request.getRequestURI());
        body.setProperty("errors", ex.violations());
        return body;
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> handleRateLimit(RateLimitExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.code().status())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
                .body(problem(ex.code(), ex.getMessage(), request.getRequestURI()));
    }

    /** Method-security denials ({@code @PreAuthorize}) surface here, not in the filter chain. */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return problem(ErrorCode.FORBIDDEN, "You do not have permission to access this resource",
                request.getRequestURI());
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return problem(ErrorCode.UNAUTHENTICATED, "Authentication required", request.getRequestURI());
    }

    /** Safety net for races the application checks could not prevent (e.g. unique constraints). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return problem(ErrorCode.CONFLICT, "The request conflicts with the current state of the resource",
                request.getRequestURI());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();
        ProblemDetail body = problem(ErrorCode.VALIDATION_FAILED, "Request has invalid fields", request.getRequestURI());
        body.setProperty("errors", violations);
        return body;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return problem(ErrorCode.CONFLICT, "The resource was modified concurrently; reload and retry",
                request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request.getRequestURI());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toViolation)
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();
        ProblemDetail body = problem(ErrorCode.VALIDATION_FAILED, "Request has invalid fields", path(request));
        body.setProperty("errors", violations);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        ProblemDetail body = problem(ErrorCode.VALIDATION_FAILED, "Request has invalid parameters", path(request));
        body.setProperty("errors", violations);
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Single choke point for framework-handled exceptions (404, 405, 415, malformed JSON, ...):
     * guarantees every body carries {@code code} and {@code correlationId}.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // super builds the ProblemDetail when body is null (e.g. 405, 415), so enrich its result.
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
                problem.setProperty("code", codeFor(status).name());
            }
            if (problem.getInstance() == null) {
                problem.setInstance(URI.create(path(request)));
            }
            problem.setProperty("correlationId", CorrelationIdFilter.current());
        }
        return response;
    }

    private static ProblemDetail problem(ErrorCode code, String detail, String path) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(code.status(), detail);
        body.setTitle(code.title());
        body.setInstance(URI.create(path));
        body.setProperty("code", code.name());
        body.setProperty("correlationId", CorrelationIdFilter.current());
        return body;
    }

    private static ErrorCode codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ErrorCode.MALFORMED_REQUEST;
            case 401 -> ErrorCode.UNAUTHENTICATED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 409 -> ErrorCode.CONFLICT;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 429 -> ErrorCode.RATE_LIMITED;
            default -> status.is4xxClientError() ? ErrorCode.MALFORMED_REQUEST : ErrorCode.INTERNAL_ERROR;
        };
    }

    private static FieldViolation toViolation(FieldError error) {
        return new FieldViolation(error.getField(), error.getDefaultMessage());
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : "";
    }
}
