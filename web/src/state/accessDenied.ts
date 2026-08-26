type Ouvinte = () => void;

const ouvintes = new Set<Ouvinte>();

/**
 * "Alguma chamada voltou `acesso_recusado`" (#90).
 *
 * Existe porque bloqueio acontece **enquanto** a pessoa usa o app: o servidor derruba a sessão e a
 * chamada seguinte volta 403, mas a aba já está montada e o portão não pergunta de novo quem ela é
 * sozinho. Sem este aviso, a pessoa bloqueada veria a mensagem de erro de uma tela qualquer no
 * lugar da explicação — e recarregar a página não é instrução que se dê a ninguém.
 *
 * Módulo separado do cliente de API de propósito: os testes de tela substituem `api/client.ts`
 * inteiro, e um portão que importasse daqui via aquele módulo quebraria em todos eles.
 */
export function subscribeAccessDenied(ouvinte: Ouvinte): () => void {
  ouvintes.add(ouvinte);
  return () => {
    ouvintes.delete(ouvinte);
  };
}

export function notifyAccessDenied(): void {
  // Cópia antes de percorrer: um ouvinte pode se desinscrever ao reagir.
  for (const ouvinte of [...ouvintes]) ouvinte();
}
