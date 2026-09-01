package dev.fos.web;

import dev.fos.curriculum.CurriculumException;
import dev.fos.service.AccessNotGrantedException;
import dev.fos.service.AdminActionException;
import dev.fos.service.AdminUserNotFoundException;
import dev.fos.service.DemoUnavailableException;
import dev.fos.service.FeedbackNotAllowedException;
import dev.fos.service.NodeNotFoundException;
import dev.fos.service.OwnerRequiredException;
import dev.fos.service.PasswordAccessException;
import dev.fos.service.QuizStaleException;
import dev.fos.service.QuizUnavailableException;
import dev.fos.service.UnauthenticatedException;
import java.security.SecureRandom;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * O alfabeto do identificador de correlação: dígitos e letras sem os pares que se confundem à
     * mão ({@code 0/O}, {@code 1/I/l}). Ele existe para ser <b>lido em voz alta ou copiado de um
     * print</b> por quem está relatando o erro — se ele fosse um UUID, ninguém o transcreveria.
     */
    private static final char[] ALFABETO = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private static final int TAMANHO_DA_CORRELACAO = 8;

    private static final SecureRandom SORTEIO = new SecureRandom();

    /**
     * Corpo de erro uniforme — o cliente trata uma forma só.
     *
     * @param correlationId preenchido <b>só</b> no 500 (#86). É o que liga um relato de usuário à
     *     linha do log: sem ele, "deu erro ao salvar" é impossível de achar num log de um dia
     *     inteiro. Nulo nos demais erros de propósito — 404 e 409 são respostas esperadas, e um
     *     identificador em cada uma delas treinaria quem lê a ignorá-lo justamente onde importa
     */
    record ApiError(String error, String message, Instant timestamp, String correlationId) {
        static ApiError of(String error, String message) {
            return new ApiError(error, message, Instant.now(), null);
        }

        static ApiError of(String error, String message, String correlationId) {
            return new ApiError(error, message, Instant.now(), correlationId);
        }
    }

    /** Curto, sorteado e sem significado: ele endereça uma linha de log, não descreve nada. */
    private static String novaCorrelacao() {
        StringBuilder id = new StringBuilder(TAMANHO_DA_CORRELACAO);
        for (int i = 0; i < TAMANHO_DA_CORRELACAO; i++) {
            id.append(ALFABETO[SORTEIO.nextInt(ALFABETO.length)]);
        }
        return id.toString();
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

    /**
     * Ação de administração que conflita com o estado atual (#89, #90).
     *
     * <p>409, e não 400: o pedido está bem formado e a rota está certa — o que não cabe é a
     * mudança. O código no corpo diz <em>qual</em> guarda bateu, porque "você não pode se rebaixar"
     * e "esta é a última conta de administração" levam a decisões diferentes de quem está do outro
     * lado da tela.
     */
    @ExceptionHandler(AdminActionException.class)
    ResponseEntity<ApiError> handleAdminAction(AdminActionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(e.code(), e.getMessage()));
    }

    @ExceptionHandler(AdminUserNotFoundException.class)
    ResponseEntity<ApiError> handleAdminUserNotFound(AdminUserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("conta_nao_encontrada", e.getMessage()));
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

    /**
     * Recusa da cadeia de segurança que estourou <em>dentro</em> do dispatch.
     *
     * <p>Normalmente estas duas são tratadas por filtro, antes de chegar ao MVC — mas se uma delas
     * chegar aqui, o {@code @ExceptionHandler(Exception.class)} abaixo a transformaria num 500, e
     * uma recusa <b>certa</b> viraria alarme de incidente.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("nao_autorizado", e.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> handleAuthentication(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of("nao_autenticado", e.getMessage()));
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
        String correlacao = novaCorrelacao();
        log.error("[{}] Currículo inválido", correlacao, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("curriculum_invalid", e.getMessage(), correlacao));
    }

    /**
     * O que não estava previsto (#86).
     *
     * <p>Existe por causa do identificador de correlação, não para esconder a exceção: sem este
     * método o Spring responde a página de erro padrão, sem código estruturado e sem nada que ligue
     * o que a pessoa viu à linha do log. Com ele, o relato "deu erro e apareceu K7QF3M2P" acha a
     * exceção num log de um dia inteiro — que é a diferença entre investigar e adivinhar.
     *
     * <p><b>A mensagem da exceção não vai no corpo.</b> Erro não previsto é justamente aquele cujo
     * texto ninguém revisou, e ele pode carregar SQL, caminho de arquivo ou valor de configuração.
     * O corpo diz que houve um erro e diz o identificador; o resto fica no log, que é de quem
     * opera.
     *
     * <p>Só pega o que chega ao {@code DispatcherServlet}. O que a cadeia de segurança recusa antes
     * disso responde sem passar por aqui — e é por isso que o alerta de incidente conta status pelo
     * filtro, e não por este método.
     *
     * <p><b>O desvio de 4xx no começo é o que impede este método de piorar as coisas.</b> Um
     * {@code @ExceptionHandler(Exception.class)} tem precedência sobre o {@code
     * DefaultHandlerExceptionResolver} do Spring, e sem o desvio o JSON malformado, o método HTTP
     * errado e o parâmetro faltando — todos hoje 400 — passariam a ser 500. Isso não seria só uma
     * resposta errada: cada um deles entraria na taxa de erro que dispara o alerta, e o
     * monitoramento passaria a avisar sobre requisições malfeitas de quem chama.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception e) {
        if (e instanceof ErrorResponse erro && erro.getStatusCode().is4xxClientError()) {
            return ResponseEntity.status(erro.getStatusCode())
                    .body(ApiError.of("requisicao_invalida", e.getMessage()));
        }
        String correlacao = novaCorrelacao();
        log.error("[{}] Erro não tratado ao atender a requisição", correlacao, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ApiError.of(
                                "erro_interno",
                                "Não foi possível concluir. Se for relatar, informe o código "
                                        + correlacao
                                        + ".",
                                correlacao));
    }
}
