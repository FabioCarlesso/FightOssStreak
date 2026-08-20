package dev.fos.config;

import dev.fos.model.AppUser;
import dev.fos.service.AccountService;
import dev.fos.service.CurrentUserProvider;
import dev.fos.service.OwnerRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * A rota do dono, só para quem é dono.
 *
 * <p>Repare no que não existe aqui dentro: nenhuma pergunta sobre o caminho da requisição. Onde a
 * regra vale é decidido pelo {@code addPathPatterns} do {@link WebMvcConfig}, que casa contra o
 * caminho já decodificado — o mesmo que o roteamento usa para escolher o controller.
 *
 * <p>Essa separação é a correção de um furo real, não preferência de estilo. A versão anterior
 * comparava {@code request.getRequestURI()} com {@code "/api/admin"}, e o Tomcat devolve esse URI
 * <em>sem decodificar</em>. Como {@code %61} é a letra {@code a} pela RFC 3986, {@code
 * /api/%61dmin/solicitacoes} endereça a mesma rota, resolvia no {@code AdminController} e não
 * entrava no {@code if}: qualquer conta aprovada lia a fila com os e-mails de quem esperava e
 * liberava quem quisesse — o poder que a D36 reserva ao autor, virando transitivo. O caso está
 * preso em {@code AuthIntegrationTest.encodedPathDoesNotBypassTheOwnerCheck}.
 *
 * <p>Continua sendo uma checagem contra {@code fos.auth.owner-emails}, e não um sistema de papéis:
 * a #24 habilita múltiplos usuários sob liberação do autor, não promove o app a multiusuário.
 */
class OwnerOnlyInterceptor implements HandlerInterceptor {

    private final CurrentUserProvider currentUser;
    private final AccountService accounts;

    OwnerOnlyInterceptor(CurrentUserProvider currentUser, AccountService accounts) {
        this.currentUser = currentUser;
        this.accounts = accounts;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        AppUser user = currentUser.currentUser();
        if (!accounts.isOwner(user)) {
            throw new OwnerRequiredException();
        }
        return true;
    }
}
