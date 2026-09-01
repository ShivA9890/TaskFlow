package dev.taskflow.identity.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> onApiException(ApiException e) {
        return body(e.getStatus(), e.getMessage());
    }

    /** Surfaces the first field error, so the UI can show something a person can act on. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> onValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("That request was not valid.");
        return body(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> onUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side.");
    }
}
