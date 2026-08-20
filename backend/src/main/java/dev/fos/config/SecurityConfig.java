package dev.fos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

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
            FosOAuth2UserService oauth2UserService)
            throws Exception {

        // Sem isto o Spring 6 adia a geração do token de CSRF, e o cookie só aparece depois que
        // alguém o lê — o app faria a primeira escrita sem token. O custo é abrir mão da proteção
        // contra BREACH, que depende de o token variar a cada resposta.
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http.authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/actuator/health",
                                                "/api/auth/providers",
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
                .csrf(
                        csrf ->
                                csrf.csrfTokenRepository(
                                                CookieCsrfTokenRepository.withHttpOnlyFalse())
                                        .csrfTokenRequestHandler(csrfHandler))
                .exceptionHandling(
                        handling ->
                                handling.authenticationEntryPoint(
                                        (request, response, exception) ->
                                                write(
                                                        objectMapper,
                                                        response,
                                                        HttpServletResponse.SC_UNAUTHORIZED,
                                                        "nao_autenticado",
                                                        "Requisição sem sessão autenticada.")))
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
                                    .userInfoEndpoint(
                                            endpoint -> endpoint.userService(oauth2UserService))
                                    .defaultSuccessUrl(AFTER_LOGIN, true));
        }

        return http.build();
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
