package dev.fos.web;

import dev.fos.curriculum.CurriculumException;
import dev.fos.service.AccessNotGrantedException;
import dev.fos.service.DemoUnavailableException;
import dev.fos.service.FeedbackNotAllowedException;
import dev.fos.service.NodeNotFoundException;
import dev.fos.service.OwnerRequiredException;
import dev.fos.service.PasswordAccessException;
import dev.fos.service.QuizStaleException;
import dev.fos.service.QuizUnavailableException;
import dev.fos.service.UnauthenticatedException;
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

    @ExceptionHandler(UnauthenticatedException.class)
    ResponseEntity<ApiError> handleUnauthenticated(UnauthenticatedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of("nao_autenticado", e.getMessage()));
    }

    /**
     * Sessão válida, conta ainda não liberada.
     *
     * <p>403 e não 401 de propósito: 401 mandaria a web de volta para o login que a pessoa acabou
     * de fazer. É o código no corpo que diz qual tela mostrar.
     */
    @ExceptionHandler(AccessNotGrantedException.class)
    ResponseEntity<ApiError> handleAccessNotGranted(AccessNotGrantedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(e.code(), e.getMessage()));
    }

    @ExceptionHandler(OwnerRequiredException.class)
    ResponseEntity<ApiError> handleOwnerRequired(OwnerRequiredException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("nao_autorizado", e.getMessage()));
    }

    /**
     * Demonstração indisponível (#62).
     *
     * <p>Um status por motivo: <b>404</b> quando o ambiente não tem conta-modelo — o recurso não
     * existe aqui —, <b>429</b> no teto de simultâneas e no freio por IP, que passam, e <b>409</b>
     * quando já há sessão de uma conta de verdade, que é conflito com o estado atual, não falta de
     * recurso.
     */
    @ExceptionHandler(DemoUnavailableException.class)
    ResponseEntity<ApiError> handleDemoUnavailable(DemoUnavailableException e) {
        HttpStatus status =
                switch (e.motivo()) {
                    case NAO_CONFIGURADA -> HttpStatus.NOT_FOUND;
                    case LOTADA -> HttpStatus.TOO_MANY_REQUESTS;
                    case SESSAO_EXISTENTE -> HttpStatus.CONFLICT;
                };
        return ResponseEntity.status(status).body(ApiError.of(e.code(), e.getMessage()));
    }

    /**
     * Entrada por senha recusada (#81).
     *
     * <p>Um status por motivo, e a escolha de cada um é a decisão: <b>401</b> para credencial que
     * não confere — sem distinguir e-mail inexistente de senha errada, senão o login vira consulta
     * de quem tem conta; <b>403</b> para senha certa e e-mail ainda não confirmado, porque 401
     * mandaria a pessoa de volta para o login que ela acabou de fazer certo; <b>429</b> no freio de
     * tentativas; e <b>503</b> quando o ambiente não tem envio de e-mail, que é indisponibilidade
     * de infraestrutura e não erro de quem pediu.
     */
    @ExceptionHandler(PasswordAccessException.class)
    ResponseEntity<ApiError> handlePasswordAccess(PasswordAccessException e) {
        HttpStatus status =
                switch (e.motivo()) {
                    case CREDENCIAL_INVALIDA -> HttpStatus.UNAUTHORIZED;
                    case EMAIL_NAO_VERIFICADO -> HttpStatus.FORBIDDEN;
                    case MUITAS_TENTATIVAS -> HttpStatus.TOO_MANY_REQUESTS;
                    case INDISPONIVEL -> HttpStatus.SERVICE_UNAVAILABLE;
                };
        return ResponseEntity.status(status).body(ApiError.of(e.code(), e.getMessage()));
    }

    @ExceptionHandler(FeedbackNotAllowedException.class)
    ResponseEntity<ApiError> handleFeedbackNotAllowed(FeedbackNotAllowedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("feedback_nao_permitido", e.getMessage()));
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

    @ExceptionHandler(QuizStaleException.class)
    ResponseEntity<ApiError> handleQuizStale(QuizStaleException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("quiz_stale", e.getMessage()));
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
