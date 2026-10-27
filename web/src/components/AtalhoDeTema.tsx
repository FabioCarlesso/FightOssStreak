import { escolherTema, useTemaEfetivo } from '../state/tema.ts';

/**
 * Atalho do cabeçalho: um clique troca entre claro e escuro (D74). Trocar por aqui é escolha
 * manual, como no seletor da tela Conta — voltar a seguir o sistema é lá, em *Sistema*.
 */
export function AtalhoDeTema() {
  const efetivo = useTemaEfetivo();
  const destino = efetivo === 'claro' ? 'escuro' : 'claro';
  const rotulo = `Usar tema ${destino}`;

  return (
    <button
      type="button"
      className="tema-atalho"
      aria-label={rotulo}
      title={rotulo}
      onClick={() => escolherTema(destino)}
    >
      <span aria-hidden="true">{destino === 'claro' ? '☀️' : '🌙'}</span>
    </button>
  );
}
