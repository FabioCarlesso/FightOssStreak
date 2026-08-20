package dev.fos.config;

import dev.fos.model.AppUser;
import dev.fos.service.AccessNotGrantedException;
import dev.fos.service.AccountService;
import dev.fos.service.CurrentUserProvider;
import dev.fos.service.OwnerRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * O portão de aprovação, em um lugar só.
 *
 * <p>Interceptor, e não {@code if (aprovado)} dentro de cada serviço: o dia em que um endpoint novo
 * esquecesse a checagem, o portão deixaria de existir para ele em silêncio. Aqui a regra vale para
 * tudo que estiver mapeado, e o que fica de fora está declarado em {@link WebMvcConfig}.
 *
 * <p>A autorização do dono mora junto de propósito. É a mesma pergunta — "esta conta pode pedir
 * isto?" — e uma checagem contra {@code fos.auth.owner-emails}, não um sistema de papéis.
 */
class AccessGateInterceptor implements HandlerInterceptor {

    private static final String ADMIN_PREFIX = "/api/admin";

    private final CurrentUserProvider currentUser;
    private final AccountService accounts;

    AccessGateInterceptor(CurrentUserProvider currentUser, AccountService accounts) {
        this.currentUser = currentUser;
        this.accounts = accounts;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Sem sessão o filtro de segurança já respondeu 401 antes de chegar aqui; currentUser()
        // relança o mesmo 401 se a conta tiver sumido no meio de uma sessão viva.
        AppUser user = currentUser.currentUser();
        if (!user.isApproved()) {
            throw new AccessNotGrantedException(user.getAccessStatus());
        }
        if (request.getRequestURI().startsWith(ADMIN_PREFIX) && !accounts.isOwner(user)) {
            throw new OwnerRequiredException();
        }
        return true;
    }
}
