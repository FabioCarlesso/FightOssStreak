package dev.fos.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Abrir e encerrar sessão nas autenticações que a aplicação faz por conta própria.
 *
 * <p>Existe porque três logins deste app não passam por filtro de autenticação do Spring — link de
 * e-mail (#52), demonstração (#62) e senha própria (#81) montam o {@code Authentication} dentro de
 * um controller. Cada um deles repetia (mal) três deveres, e o terceiro seria o terceiro lugar a
 * repetir:
 *
 * <ol>
 *   <li><b>Rotacionar o id da sessão.</b> Quem chega ao login já tem sessão — o cookie de CSRF a
 *       cria. Autenticar sem trocar o id deixa valendo um identificador que alguém pode ter
 *       plantado antes (session fixation), e é o próprio {@code SessionFixationProtection} que o
 *       Spring aplica sozinho nos logins que passam pela cadeia de filtros.
 *   <li><b>Gravar o contexto no repositório de sessão.</b> Sem isto a sessão morre com a requisição
 *       e a pessoa volta para a tela de login — o sintoma da #51 por outro caminho.
 *   <li><b>Registrar a sessão.</b> É o que permite encerrá-la depois sem ter o objeto {@code
 *       HttpSession} em mãos, e é do que depende a redefinição de senha derrubar o que está aberto.
 * </ol>
 */
@Component
public class SessionLogin {

    private final SecurityContextRepository contextRepository =
            new HttpSessionSecurityContextRepository();

    private final SessionRegistry sessionRegistry;

    public SessionLogin(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    /** Abre a sessão autenticada, com id novo. */
    public void signIn(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        HttpSession session = request.getSession(false);
        if (session != null) {
            sessionRegistry.registerNewSession(session.getId(), authentication.getPrincipal());
        }
    }

    /**
     * Encerra toda sessão aberta por estes subjects.
     *
     * <p>Recebe os {@code provider_subject} da conta, e não um id de usuário, porque é isso que o
     * registro guarda: o principal de cada sessão. A comparação é pelo <em>nome</em> do principal,
     * o que faz a regra valer igual para as três autenticações próprias (principal é o e-mail ou o
     * subject sorteado) e para o login por provedor (principal é o {@code OAuth2User}, cujo nome é
     * o subject). Sem isso, redefinir a senha derrubaria só as sessões de senha e deixaria de pé
     * exatamente a sessão de quem já estava dentro.
     *
     * <p>Marcar como expirada, e não invalidar: quem invalida é o {@code ConcurrentSessionFilter}
     * na requisição seguinte daquela sessão, que é a única que tem o {@code HttpSession} em mãos.
     */
    public void endSessionsOf(Collection<String> subjects) {
        if (subjects.isEmpty()) {
            return;
        }
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (!subjects.contains(nameOf(principal))) {
                continue;
            }
            List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, true);
            sessions.forEach(SessionInformation::expireNow);
        }
    }

    private static String nameOf(Object principal) {
        if (principal instanceof AuthenticatedPrincipal named) {
            return named.getName();
        }
        if (principal instanceof java.security.Principal named) {
            return named.getName();
        }
        return String.valueOf(principal);
    }
}
