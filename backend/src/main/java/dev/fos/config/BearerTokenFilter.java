package dev.fos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fos.service.MobileTokenAuthentication;
import dev.fos.service.MobileTokens;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica a requisição do app mobile pelo cabeçalho {@code Authorization} (#139, D68).
 *
 * <p><b>Requisição com {@code Authorization} é decidida só pelo token.</b> Se o cabeçalho existe e
 * não traz um token válido, a resposta é 401 aqui mesmo. Ela roda na cadeia sem estado da {@code
 * SecurityConfig} ({@code mobileFilterChain}), que nem lê sessão: um cookie de sessão que viesse
 * junto não salva um token ruim, e é isso que torna seguro aquela cadeia dispensar o CSRF.
 *
 * <p>O contexto vale para a requisição e morre com ela. O app nunca recebe {@code JSESSIONID}, e
 * por isso a sessão em memória cair num deploy não derruba o login de aparelho nenhum.
 *
 * <p>Não é bean, de propósito: filtro registrado como bean o Spring Boot também pendura direto no
 * container, e ele rodaria duas vezes, uma delas fora da cadeia de segurança.
 */
class BearerTokenFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    /**
     * Quem traz {@code Authorization} — o critério que dispensa o CSRF na {@code SecurityConfig}.
     */
    static final RequestMatcher HAS_AUTHORIZATION =
            request -> request.getHeader(HttpHeaders.AUTHORIZATION) != null;

    private final MobileTokens tokens;
    private final ObjectMapper objectMapper;

    BearerTokenFilter(MobileTokens tokens, ObjectMapper objectMapper) {
        this.tokens = tokens;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null) {
            chain.doFilter(request, response);
            return;
        }
        Optional<Long> identity =
                header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())
                        ? tokens.authenticate(header.substring(PREFIX.length()))
                        : Optional.empty();
        if (identity.isEmpty()) {
            SecurityConfig.write(
                    objectMapper,
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "token_invalido",
                    "Token do app ausente, vencido ou revogado. Entre de novo.");
            return;
        }
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new MobileTokenAuthentication(identity.get()));
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
