package dev.fos.config;

import dev.fos.service.AccountService;
import dev.fos.service.CurrentUserProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS do dev server e registro do portão de aprovação.
 *
 * <p>O CORS é restrito a localhost de propósito: em produção web e API são servidas pela mesma
 * origem, e um CORS aberto aqui viraria um curinga esquecido depois.
 */
@Configuration
class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserProvider currentUser;
    private final AccountService accounts;

    WebMvcConfig(CurrentUserProvider currentUser, AccountService accounts) {
        this.currentUser = currentUser;
        this.accounts = accounts;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // Sem isto o dev server não recebe nem manda o cookie de sessão, e o login
                // simplesmente não existiria para quem roda `npm run dev:web`.
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AccessGateInterceptor(currentUser, accounts))
                .addPathPatterns("/api/**")
                // O que a conta pendente ainda pode: saber em que estado está, sair e se excluir.
                // Sem estas exceções a tela de "solicitação registrada" não teria o que mostrar, e
                // quem pediu acesso e não entrou ficaria sem como sumir do banco (LGPD).
                .excludePathPatterns("/api/me", "/api/logout", "/api/auth/**");
    }
}
