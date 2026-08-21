package dev.fos.web.dto;

import java.time.Instant;

/** Acesso demonstrativo: a resposta de quem acabou de abrir uma demonstração (#62). */
public final class DemoDtos {

    private DemoDtos() {}

    /**
     * Sessão de demonstração recém-aberta.
     *
     * @param destino para onde a web deve ir agora. Vem do servidor, e não fixo no cliente, porque
     *     quem sabe o que a sessão já pode ver é quem a abriu
     * @param expiraEm quando a conta deixa de existir para quem está do outro lado
     */
    public record DemoSessionView(String destino, Instant expiraEm) {}
}
