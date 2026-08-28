package dev.fos.model;

/**
 * O que aconteceu (#84, D50).
 *
 * <p>Duas naturezas, e a fronteira entre elas é de confiança: {@link #PAGINA} é a única que o
 * cliente pode disparar, e por isso é a única que não responde nada sobre o funil. Os outros quatro
 * são emitidos pelo backend, no ponto em que o fato de fato acontece — vindos do cliente seriam
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
    PRIMEIRO_DRILL
}
