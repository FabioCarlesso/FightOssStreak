package dev.fos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.fos.model.UsageEvent;
import dev.fos.model.UsageEventType;
import dev.fos.repo.UsageEventRepository;
import dev.fos.service.UsageCollector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * O teto diário da coleta (#84, D50) — o único limite que não depende do cliente.
 *
 * <p>O freio por visita é chaveado na {@code VisitKey}, que é hash de IP + {@code User-Agent}. A
 * #77 tirou o IP das mãos de quem chama, mas o {@code User-Agent} é do cliente por definição:
 * variá-lo dá chave nova a cada requisição, e aquele teto nunca é alcançado — foi medido em uma
 * instância de verdade: 400 requisições do mesmo IP com {@code User-Agent} rodando gravaram as 400.
 * Este teste garante que existe um limite que essa manobra não contorna.
 *
 * <p>Ele não é filtro de abuso: quem abusa gasta o orçamento do dia e a coleta legítima para junto.
 * É <b>teto de estrago</b> — a tabela deixa de crescer sem limite.
 *
 * <p>Um método só, e não dois, porque o contador do dia vive no {@code UsageCollector} e o contexto
 * é compartilhado entre os testes da classe: separá-los faria o segundo começar com o orçamento que
 * o primeiro gastou.
 */
@SpringBootTest(properties = "fos.usage.daily-cap=3")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UsageTetoDiarioIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsageEventRepository events;
    @Autowired private UsageCollector usage;

    @BeforeEach
    void limpar() {
        events.deleteAll();
    }

    @AfterEach
    void soltarARequisicao() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("o teto do dia segura o acesso mesmo com User-Agent rodando — o funil passa")
    void oTetoSeguraOAcessoEDeixaOFunilPassar() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(evento("ua-" + i)).andExpect(status().isNoContent());
        }
        assertThat(events.count()).isEqualTo(3);

        // User-Agent novo a cada requisição: chave de visita nova, freio por visita nunca
        // alcançado — e é exatamente por isso que este teto existe. A resposta segue 204: o
        // cliente não tem o que fazer com a recusa, e a coleta nunca vira erro na tela.
        for (int i = 3; i < 10; i++) {
            mockMvc.perform(evento("ua-" + i)).andExpect(status().isNoContent());
        }

        assertThat(events.count())
                .as("variar o User-Agent não pode contornar o teto do dia")
                .isEqualTo(3);

        // Com o orçamento do dia esgotado, o degrau do funil ainda entra: ele é emitido pelo
        // backend, já é limitado pela ação de verdade que o produz, e perdê-lo seria perder
        // justamente o número que a issue existe para produzir.
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
        usage.funnel(UsageEventType.CADASTRO_CRIADO);

        assertThat(events.findAll())
                .extracting(UsageEvent::getEventType)
                .containsOnlyOnce(UsageEventType.CADASTRO_CRIADO);
    }

    private static MockHttpServletRequestBuilder evento(String userAgent) {
        return post("/api/telemetria/evento")
                .contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", userAgent)
                .content("{\"caminho\":\"/\"}");
    }
}
