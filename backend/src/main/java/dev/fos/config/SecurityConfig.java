package dev.fos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Quem entra, como a sessão é protegida e por onde o login passa.
 *
 * <p>Três decisões que não são default e por isso estão comentadas: o fluxo OAuth vive sob {@code
 * /api}, a resposta a "sem sessão" é 401 em JSON (não redirect para tela de login), e a aplicação
 * sobe sem nenhum provedor configurado.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    /** Para onde o browser volta depois do login. É a primeira tela do app. */
    private static final String AFTER_LOGIN = "/hoje";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            FosOAuth2UserService oauth2UserService,
            FosOidcUserService oidcUserService,
            SessionRegistry sessionRegistry)
            throws Exception {

        // Sem isto o Spring 6 adia a geração do token de CSRF, e o cookie só aparece depois que
        // alguém o lê — o app faria a primeira escrita sem token. O custo é abrir mão da proteção
        // contra BREACH, que depende de o token variar a cada resposta.
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http.cors(org.springframework.security.config.Customizer.withDefaults())
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/actuator/health",
                                                "/api/auth/providers",
                                                // Cadastro com senha própria (#81): é a porta de
                                                // quem ainda não tem conta, então exigir sessão
                                                // aqui seria pedir conta a quem vem criar uma.
                                                "/api/auth/cadastro",
                                                "/api/auth/login",
                                                "/api/auth/verificar/**",
                                                "/api/auth/verificacao/**",
                                                "/api/auth/senha/**",
                                                // A demonstração é degrau ANTES do portão: quem
                                                // ainda não tem conta é justamente quem a abre
                                                // (#62).
                                                "/api/demo/**",
                                                // A coleta de uso (#84) mede quem AINDA NÃO tem
                                                // conta: a landing é a página que recebe o link.
                                                // Exigir sessão aqui mediria só quem já entrou.
                                                "/api/telemetria/**",
                                                "/api/oauth2/**",
                                                "/api/login/**")
                                        .permitAll()
                                        // O spec alimenta a geração de tipos do front e é
                                        // conferido pelo CI. O backend não tem domínio público
                                        // (D24), então isto não é superfície exposta.
                                        .requestMatchers(
                                                "/v3/api-docs",
                                                "/v3/api-docs/**",
                                                "/swagger-ui.html",
                                                "/swagger-ui/**")
                                        .permitAll()
                                        .requestMatchers("/api/**")
                                        .authenticated()
                                        .anyRequest()
                                        .permitAll())
                // O registro de sessões existe para a redefinição de senha poder derrubar o que
                // está aberto (#81). `maximumSessions(-1)` não limita nada — é o que registra o
                // `ConcurrentSessionFilter`, sem o qual marcar uma sessão como expirada não teria
                // efeito nenhum na requisição seguinte dela.
                .sessionManagement(
                        session ->
                                session.maximumSessions(-1)
                                        .sessionRegistry(sessionRegistry)
                                        .expiredSessionStrategy(
                                                event ->
                                                        write(
                                                                objectMapper,
                                                                event.getResponse(),
                                                                HttpServletResponse.SC_UNAUTHORIZED,
                                                                "sessao_encerrada",
                                                                "Esta sessão foi encerrada. Entre"
                                                                        + " de novo.")))
                .csrf(
                        csrf ->
                                csrf.csrfTokenRepository(
                                                CookieCsrfTokenRepository.withHttpOnlyFalse())
                                        .csrfTokenRequestHandler(csrfHandler)
                                        // A coleta de uso (#84) é a única escrita fora do CSRF, e
                                        // por um motivo e não por conveniência: a primeira coisa
                                        // que o app faz é registrar o acesso à landing, ANTES de
                                        // qualquer resposta ter deixado o cookie de token — o
                                        // primeiro evento de toda visita morreria em 403. E não há
                                        // o que proteger: o endpoint não lê nem muda estado de
                                        // conta nenhuma, e já aceita requisição sem sessão de
                                        // qualquer origem. Forjá-lo suja a métrica de quem forjou,
                                        // que é o que o freio por chave de visita limita.
                                        .ignoringRequestMatchers("/api/telemetria/**"))
                .exceptionHandling(
                        handling ->
                                handling.authenticationEntryPoint(
                                                (request, response, exception) ->
                                                        write(
                                                                objectMapper,
                                                                response,
                                                                HttpServletResponse.SC_UNAUTHORIZED,
                                                                "nao_autenticado",
                                                                "Requisição sem sessão"
                                                                        + " autenticada."))
                                        // Sem isto o 403 do filtro de CSRF sai no corpo padrão do
                                        // Boot ({timestamp, status, error, path}), que não tem
                                        // `message` e traz "Forbidden" onde o cliente lê o código.
                                        // A tela mostrava um "403" pelado justamente quando dá
                                        // para dizer o que fazer: recarregar e repetir.
                                        .accessDeniedHandler(
                                                (request, response, exception) ->
                                                        write(
                                                                objectMapper,
                                                                response,
                                                                HttpServletResponse.SC_FORBIDDEN,
                                                                exception instanceof CsrfException
                                                                        ? "csrf_invalido"
                                                                        : "acesso_negado",
                                                                exception instanceof CsrfException
                                                                        ? "Token de proteção contra"
                                                                                + " CSRF ausente ou"
                                                                                + " vencido. Recarregue"
                                                                                + " a página e tente de"
                                                                                + " novo."
                                                                        : "Requisição recusada.")))
                .logout(
                        logout ->
                                logout.logoutUrl("/api/logout")
                                        .deleteCookies("JSESSIONID")
                                        .logoutSuccessHandler(
                                                (request, response, authentication) ->
                                                        response.setStatus(
                                                                HttpServletResponse
                                                                        .SC_NO_CONTENT)));

        // Sem provedor configurado não existe ClientRegistrationRepository, e ligar o oauth2Login
        // aqui derrubaria a subida da aplicação. É o que faz `./mvnw test` e o dev rodarem sem
        // nenhum segredo.
        ClientRegistrationRepository registrations = clientRegistrations.getIfAvailable();
        if (registrations != null) {
            http.oauth2Login(
                    login ->
                            login.authorizationEndpoint(
                                            endpoint ->
                                                    endpoint.baseUri("/api/oauth2/authorization"))
                                    .redirectionEndpoint(
                                            endpoint ->
                                                    endpoint.baseUri("/api/login/oauth2/code/*"))
                                    // Os DOIS, e não um: o Spring escolhe pelo escopo pedido.
                                    // `openid` (Google) vai para o oidcUserService; sem ele
                                    // (Facebook), para o userService. Registrar só um faz o login
                                    // do outro autenticar sem criar conta — e o app responder 401
                                    // para sempre, sem erro nenhum no log.
                                    .userInfoEndpoint(
                                            endpoint ->
                                                    endpoint.userService(oauth2UserService)
                                                            .oidcUserService(oidcUserService))
                                    .defaultSuccessUrl(AFTER_LOGIN, true));
        }

        return http.build();
    }

    /**
     * Codificador de senha (#81).
     *
     * <p>O delegador do Spring, e não o bcrypt cru: ele grava o prefixo do algoritmo no próprio
     * hash ({@code {bcrypt}...}), então trocar de função depois é rehash no login de cada pessoa —
     * e não uma migration que ninguém consegue escrever, porque hash antigo não se converte. É o
     * mesmo motivo de a coluna ser larga.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Onde as sessões abertas ficam anotadas.
     *
     * <p>Existe para uma coisa só: redefinir a senha precisa encerrar o que está aberto, e sem
     * registro não há como alcançar uma sessão que não é a da requisição atual. Os logins que a
     * aplicação faz por conta própria se anotam aqui pelo {@code SessionLogin}; os que passam pela
     * cadeia de filtros (OAuth) são anotados pelo próprio Spring, por causa do {@code
     * maximumSessions} acima.
     */
    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * Sem isto o registro só cresce: é o publisher que avisa a sessão destruída, e o {@code
     * SessionRegistryImpl} só remove a entrada quando recebe esse evento.
     */
    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /**
     * CORS do dev server do Vite.
     *
     * <p>Precisa ser um bean lido pela cadeia de segurança, e não `addCorsMappings` de um
     * `WebMvcConfigurer`: desde que existe autenticação, é o filtro que responde primeiro, e o
     * preflight (que não carrega cookie) morria em 401 antes de o MVC ver a requisição. Restrito a
     * localhost de propósito — em produção web e API são a mesma origem, e um curinga aqui viraria
     * um esquecimento permanente.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // Sem credencial não há sessão do outro lado, e toda chamada voltaria 401.
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private static void write(
            ObjectMapper objectMapper,
            HttpServletResponse response,
            int status,
            String error,
            String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // Mesma forma do corpo de erro do resto da API (ApiExceptionHandler.ApiError): o cliente
        // trata uma forma só, venha o erro do filtro de segurança ou de um controller.
        objectMapper.writeValue(
                response.getWriter(),
                Map.of("error", error, "message", message, "timestamp", Instant.now().toString()));
    }
}
