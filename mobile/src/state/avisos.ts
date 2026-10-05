type Ouvinte = () => void;

/**
 * Avisos que qualquer chamada pode disparar e só o portão sabe tratar, como no web (#90).
 *
 * - `semSessao`: o token morreu (vencido, revogado, senha trocada). O app volta ao login.
 * - `acessoRecusado`: a conta foi bloqueada no meio do uso. O portão reconsulta a conta e mostra o
 *   motivo, em vez de cada tela exibir um erro próprio.
 */
function canal() {
  const ouvintes = new Set<Ouvinte>();
  return {
    assinar(ouvinte: Ouvinte): () => void {
      ouvintes.add(ouvinte);
      return () => {
        ouvintes.delete(ouvinte);
      };
    },
    avisar(): void {
      // Cópia antes de percorrer: um ouvinte pode se desinscrever ao reagir.
      for (const ouvinte of [...ouvintes]) ouvinte();
    },
  };
}

export const semSessao = canal();
export const acessoRecusado = canal();
