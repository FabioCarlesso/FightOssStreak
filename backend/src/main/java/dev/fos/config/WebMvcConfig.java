package dev.fos.config;

import dev.fos.service.AccountService;
import dev.fos.service.CurrentUserProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registro do portão de aprovação.
 *
 * <p>O CORS morava aqui e mudou para o {@code SecurityConfig}: com autenticação, quem responde
 * primeiro é a cadeia de segurança, e um `addCorsMappings` que o MVC nunca chega a aplicar é pior
 * que nenhum — parece configurado e não está.
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
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AccessGateInterceptor(currentUser, accounts))
                .addPathPatterns("/api/**")
                // O que a conta pendente ainda pode: saber em que estado está, sair e se excluir.
                // Sem estas exceções a tela de "solicitação registrada" não teria o que mostrar, e
                // quem pediu acesso e não entrou ficaria sem como sumir do banco (LGPD).
                .excludePathPatterns("/api/me", "/api/logout", "/api/auth/**");
    }
}
