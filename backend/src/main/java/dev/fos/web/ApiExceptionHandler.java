package dev.fos.web;

import dev.fos.curriculum.CurriculumException;
import dev.fos.service.NodeNotFoundException;
import dev.fos.service.QuizUnavailableException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Corpo de erro uniforme — o cliente trata uma forma só. */
    record ApiError(String error, String message, Instant timestamp) {
        static ApiError of(String error, String message) {
            return new ApiError(error, message, Instant.now());
        }
    }

    @ExceptionHandler(NodeNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(NodeNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("node_not_found", e.getMessage()));
    }

    @ExceptionHandler(QuizUnavailableException.class)
    ResponseEntity<ApiError> handleQuizUnavailable(QuizUnavailableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("quiz_unavailable", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiError.of("invalid_request", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        String detail =
                e.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getField() + ": " + error.getDefaultMessage())
                        .findFirst()
                        .orElse("payload inválido");
        return ResponseEntity.badRequest().body(ApiError.of("invalid_request", detail));
    }

    @ExceptionHandler(CurriculumException.class)
    ResponseEntity<ApiError> handleCurriculum(CurriculumException e) {
        log.error("Currículo inválido", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("curriculum_invalid", e.getMessage()));
    }
}
