package dev.fos.model;

/**
 * O que aconteceu (#84, D50).
 *
 * <p>Duas naturezas, e a fronteira entre elas é de confiança: {@link #PAGINA} é a única que o
 * cliente pode disparar, e por isso é a única que não responde nada sobre o funil. Os outros são
 * emitidos pelo backend, no ponto em que o fato de fato acontece — vindos do cliente seriam
 * forjáveis, e "a abertura funcionou?" é exatamente a pergunta que não se responde com número que
 * qualquer um pode inflar.
 */
public enum UsageEventType {

    /** Mudança de rota no app. É o acesso — o que responde "quantas pessoas chegaram". */
    PAGINA,

    /** Demonstração aberta (D39): alguém quis ver o app funcionando. */
    DEMONSTRACAO_ABERTA,

    /** Conta criada com e-mail e senha (D47). Ainda não é conta usável — falta confirmar. */
    CADASTRO_CRIADO,

    /** E-mail confirmado: o cadastro virou conta de verdade. */
    EMAIL_VERIFICADO,

    /**
     * Primeiro drill registrado por uma conta. É o degrau que diz que o app foi usado, não só
     * aberto.
     */
    PRIMEIRO_DRILL,

    /**
     * A conta registrou drill num <b>segundo</b> dia distinto, dentro de sete dias do primeiro
     * (#85).
     *
     * <p>É o último degrau do funil, e o único que não fala de chegar: fala de <b>voltar</b>. Um
     * app de revisão que só é aberto uma vez falhou, e nenhum dos degraus anteriores diz isso.
     *
     * <p>A definição é em <em>dias distintos com drill</em>, e não em "abriu de novo", por dois
     * motivos: acesso de tela não é ligável entre dias — a chave de visita muda com o sal diário
     * (D50), de propósito —, e voltar para olhar não é voltar para treinar. Contado assim, o evento
     * sai uma vez só por conta, no instante em que o segundo dia aparece.
     */
    RETORNO_EM_7_DIAS
}
