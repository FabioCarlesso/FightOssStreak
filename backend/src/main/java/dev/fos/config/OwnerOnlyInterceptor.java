package dev.fos.config;

import dev.fos.model.AppUser;
import dev.fos.model.Role;
import dev.fos.service.AccountService;
import dev.fos.service.CurrentUserProvider;
import dev.fos.service.OwnerRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * As rotas de administração, só para quem administra.
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
 * <p>Desde a D48 este é o <b>único</b> ponto do app que pergunta "esta requisição é de
 * administração?", e a resposta vem de {@code AccountService.roleOf}. O que mudou com a D49 foi
 * onde {@code roleOf} busca a resposta — {@code app_user.role} em vez de {@code
 * fos.auth.owner-emails} —, e este arquivo não precisou saber disso, que era o ponto de ter um
 * lugar só. Continuam sendo dois papéis: permissão granular segue fora de escopo.
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
        if (accounts.roleOf(user) != Role.ADMIN) {
            throw new OwnerRequiredException();
        }
        return true;
    }
}
