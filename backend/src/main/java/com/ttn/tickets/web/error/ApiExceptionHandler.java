package com.ttn.tickets.web.error;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;

import com.ttn.tickets.application.TicketNotFoundException;
import com.ttn.tickets.domain.InvalidTransitionException;
import com.ttn.tickets.domain.ResolutionNotesNotEditableException;
import com.ttn.tickets.domain.TicketFrozenException;
import com.ttn.tickets.domain.TicketStatus;
import com.ttn.tickets.web.validation.AllowedValues;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final URI ABOUT_BLANK = URI.create("about:blank");

    private final int lockWaitRetryAfterSeconds;

    ApiExceptionHandler(@Value("${ticket.lock-wait-retry-after-seconds:5}") int lockWaitRetryAfterSeconds) {
        this.lockWaitRetryAfterSeconds = lockWaitRetryAfterSeconds;
    }

    @ExceptionHandler(TicketNotFoundException.class)
    ResponseEntity<Object> ticketNotFound(TicketNotFoundException exception) {
        return body(ErrorCode.TICKET_NOT_FOUND, HttpStatus.NOT_FOUND, "Ticket not found", exception.getMessage(), null);
    }

    @ExceptionHandler(InvalidTransitionException.class)
    ResponseEntity<Object> invalidTransition(InvalidTransitionException exception) {
        ProblemDetail problem = problem(ErrorCode.INVALID_TRANSITION, HttpStatus.CONFLICT, "Invalid transition",
                exception.getMessage());
        problem.setProperty("currentStatus", exception.currentStatus().name());
        problem.setProperty("allowedTransitions",
                exception.allowedTransitions().stream().map(TicketStatus::name).toList());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(TicketFrozenException.class)
    ResponseEntity<Object> ticketFrozen(TicketFrozenException exception) {
        return stateConflict(ErrorCode.TICKET_FROZEN, "Ticket frozen", exception.getMessage(),
                exception.currentStatus());
    }

    @ExceptionHandler(ResolutionNotesNotEditableException.class)
    ResponseEntity<Object> resolutionNotesNotEditable(ResolutionNotesNotEditableException exception) {
        return stateConflict(ErrorCode.RESOLUTION_NOTES_NOT_EDITABLE, "Resolution notes not editable",
                exception.getMessage(), exception.currentStatus());
    }

    @ExceptionHandler({CannotAcquireLockException.class, PessimisticLockingFailureException.class})
    ResponseEntity<Object> ticketBusy(Exception exception) {
        if (!isLockTimeout(exception)) {
            log.error("Unhandled lock failure", exception);
            return body(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
                    "The request could not be completed", null);
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, Integer.toString(lockWaitRetryAfterSeconds))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem(ErrorCode.TICKET_BUSY, HttpStatus.SERVICE_UNAVAILABLE, "Ticket busy",
                        "The ticket is held by another change; retry later"));
    }

    @ExceptionHandler(ValidationFailedException.class)
    ResponseEntity<Object> validationFailed(ValidationFailedException exception) {
        return body(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid", exception.errors());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> internal(Exception exception) {
        log.error("Unhandled error", exception);
        return body(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
                "The request could not be completed", null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return body(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid", fieldErrors(exception));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiFieldError> errors = new ArrayList<>();
        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            String field = parameterName(result.getMethodParameter());
            for (MessageSourceResolvable resolvable : result.getResolvableErrors()) {
                errors.add(fieldError(field, result.unwrap(resolvable, ConstraintViolation.class)));
            }
        }
        return body(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid", errors);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String field = exception instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : exception.getPropertyName();
        return body(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid",
                List.of(new ApiFieldError(field == null ? "request" : field, "INVALID", "is not valid", null)));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        UnrecognizedPropertyException unknown = unrecognized(exception);
        List<ApiFieldError> errors = unknown == null
                ? null
                : List.of(new ApiFieldError(unknown.getPropertyName(), "UNKNOWN_FIELD", "Unknown field", null));
        return body(ErrorCode.MALFORMED_REQUEST, HttpStatus.BAD_REQUEST, "Malformed request",
                "The request body could not be read", errors);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException exception, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        return body(ErrorCode.METHOD_NOT_ALLOWED, HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed",
                "The method is not allowed for this resource", null);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return body(ErrorCode.UNSUPPORTED_MEDIA_TYPE, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type",
                "The content type is not supported", null);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return body(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Not found", "No such resource", null);
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(NoHandlerFoundException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return body(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Not found", "No such resource", null);
    }

    private static List<ApiFieldError> fieldErrors(MethodArgumentNotValidException exception) {
        List<ApiFieldError> errors = new ArrayList<>();
        exception.getBindingResult().getFieldErrors().forEach(fieldError -> {
            ConstraintViolation<?> violation = fieldError.unwrap(ConstraintViolation.class);
            Annotation annotation = violation.getConstraintDescriptor().getAnnotation();
            List<String> allowed = annotation instanceof AllowedValues allowedValues
                    ? List.of(allowedValues.allowedValues())
                    : null;
            errors.add(new ApiFieldError(fieldError.getField(), codeFor(annotation), fieldError.getDefaultMessage(),
                    allowed));
        });
        return errors;
    }

    private static ApiFieldError fieldError(String field, ConstraintViolation<?> violation) {
        Annotation annotation = violation.getConstraintDescriptor().getAnnotation();
        List<String> allowed = annotation instanceof AllowedValues allowedValues
                ? List.of(allowedValues.allowedValues())
                : null;
        return new ApiFieldError(field, codeFor(annotation), violation.getMessage(), allowed);
    }

    private static String parameterName(MethodParameter parameter) {
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null && !requestParam.name().isEmpty()) {
            return requestParam.name();
        }
        String name = parameter.getParameterName();
        return name == null ? "request" : name;
    }

    private static String codeFor(Annotation annotation) {
        if (annotation instanceof NotBlank) {
            return "BLANK";
        }
        if (annotation instanceof NotNull) {
            return "REQUIRED";
        }
        if (annotation instanceof Size) {
            return "TOO_LONG";
        }
        if (annotation instanceof AllowedValues) {
            return "NOT_ALLOWED";
        }
        return "INVALID";
    }

    private static UnrecognizedPropertyException unrecognized(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof UnrecognizedPropertyException unrecognized) {
                return unrecognized;
            }
        }
        return null;
    }

    private static ResponseEntity<Object> stateConflict(ErrorCode code, String title, String detail,
            TicketStatus currentStatus) {
        ProblemDetail problem = problem(code, HttpStatus.CONFLICT, title, detail);
        problem.setProperty("currentStatus", currentStatus.name());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static ResponseEntity<Object> body(ErrorCode code, HttpStatus status, String title, String detail,
            List<ApiFieldError> errors) {
        ProblemDetail problem = problem(code, status, title, detail);
        if (errors != null) {
            problem.setProperty("errors", errors);
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static ProblemDetail problem(ErrorCode code, HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(ABOUT_BLANK);
        problem.setTitle(title);
        problem.setProperty("errorCode", code.name());
        return problem;
    }

    private static boolean isLockTimeout(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql && "55P03".equals(sql.getSQLState())) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("lock timeout")) {
                return true;
            }
        }
        return false;
    }
}
