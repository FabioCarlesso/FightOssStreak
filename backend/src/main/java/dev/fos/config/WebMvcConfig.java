package dev.fos.config;

import dev.fos.service.AccountService;
import dev.fos.service.CurrentUserProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registro dos portões: quem está liberado, e quem é o dono.
 *
 * <p>É aqui que mora a resposta para "onde cada regra vale", e de propósito: o {@code
 * InterceptorRegistry} casa os padrões contra o caminho <em>decodificado</em> da requisição, que é
 * o mesmo que o roteamento usa para escolher o controller. Interceptor que pergunta o caminho por
 * conta própria pode discordar do roteamento — foi assim que a rota do dono já ficou aberta para
 * qualquer conta aprovada (ver {@link OwnerOnlyInterceptor}).
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
        registry.addInterceptor(new AccessGateInterceptor(currentUser))
                .addPathPatterns("/api/**")
                // O que a conta pendente ainda pode: saber em que estado está, sair e se excluir.
                // Sem estas exceções a tela de "solicitação registrada" não teria o que mostrar, e
                // quem pediu acesso e não entrou ficaria sem como sumir do banco (LGPD).
                // `/api/demo/**` abre a sessão: exigir sessão nele seria pedir conta a quem vem
                // pedir uma conta.
                .excludePathPatterns(
                        "/api/me", "/api/logout", "/api/auth/**", "/api/login/**", "/api/demo/**");

        // Depois do portão de aprovação, e não antes: a ordem de registro é a de execução, e
        // "esta conta está liberada?" precisa ser respondida antes de "esta conta é a dona?".
        // Trocar a ordem faria conta pendente receber 403 de rota restrita em vez do motivo real,
        // e a web escolhe a tela pelo motivo.
        registry.addInterceptor(new OwnerOnlyInterceptor(currentUser, accounts))
                .addPathPatterns("/api/admin/**");
    }
}
