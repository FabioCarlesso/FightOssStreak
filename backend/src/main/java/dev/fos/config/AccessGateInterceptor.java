package dev.fos.config;

import dev.fos.model.AppUser;
import dev.fos.service.AccessNotGrantedException;
import dev.fos.service.CurrentUserProvider;
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
 * <p>A autorização do dono morava aqui junto e saiu para o {@link OwnerOnlyInterceptor}. O motivo
 * não é organização: ela perguntava por caminho, e perguntar por caminho aqui dentro era o defeito.
 */
class AccessGateInterceptor implements HandlerInterceptor {

    private final CurrentUserProvider currentUser;

    AccessGateInterceptor(CurrentUserProvider currentUser) {
        this.currentUser = currentUser;
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
        return true;
    }
}
