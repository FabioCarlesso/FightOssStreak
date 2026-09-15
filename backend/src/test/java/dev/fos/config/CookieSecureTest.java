package dev.fos.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * O flag {@code Secure} do cookie de sessão é ambiente, não constante (D60): {@code true} onde há
 * TLS na borda, {@code false} em dev e no Compose, que servem em {@code http} e perderiam a sessão.
 *
 * <p>O que este teste protege é a <b>ligação</b>, não o valor. {@code ServerProperties} ignora
 * chave desconhecida, então um erro de digitação sob {@code cookie:} não quebra a subida — deixa o
 * flag em {@code null}, que é "não marque", exatamente o defeito da #74 de volta e em silêncio. Por
 * isso a asserção é sobre {@code FALSE} e não sobre "falsy": o default precisa ter sido
 * <b>lido</b>.
 *
 * <p>Que o valor de produção chega pelo ambiente não dá para provar aqui — quem prova é o {@code
 * curl} anotado no README, porque o que quebra não é o código, é a variável não subir junto com o
 * deploy (foi assim na #96, com outra variável).
 */
@SpringBootTest
@ActiveProfiles("test")
class CookieSecureTest {

    @Autowired private ServerProperties server;

    @Test
    @DisplayName("o default do cookie de sessão é lido, e é o valor que dev e Compose precisam")
    void defaultEhLidoEFalso() {
        assertThat(server.getServlet().getSession().getCookie().getSecure()).isFalse();
    }
}
