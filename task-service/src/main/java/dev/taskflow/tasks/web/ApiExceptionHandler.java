
package dev.taskflow.tasks.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> onValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("That request was not valid.");
        return body(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    /** Someone else saved this task while the user had it open. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> onStaleWrite(
            ObjectOptimisticLockingFailureException e) {
        return body(HttpStatus.CONFLICT,
                "Someone else changed this task. Reload and try again.");
    }

    /**
     * Surfaces the column-bounds trigger. The message comes from the database, so
     * the rule has exactly one wording wherever it is violated.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> onConstraint(DataIntegrityViolationException e) {
        String root = e.getMostSpecificCause().getMessage();
        if (root != null && root.contains("columns")) {
            String cleaned = root.lines().findFirst().orElse(root)
                    .replaceFirst("^ERROR:\\s*", "").trim();
            return body(HttpStatus.UNPROCESSABLE_ENTITY, cleaned);
        }
        log.warn("Constraint violation", e);
        return body(HttpStatus.UNPROCESSABLE_ENTITY, "That change was not allowed.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> onUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side.");
    }
}
