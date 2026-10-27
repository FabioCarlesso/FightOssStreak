import { OPCOES_DE_TEMA, useTema } from '../state/tema.ts';

/**
 * Sistema, escuro ou claro (D74, #187). Troca na hora, sem recarregar, e a escolha fica só neste
 * navegador — por isso a frase diz "neste aparelho".
 */
export function EscolhaDeTema() {
  const [preferencia, escolher] = useTema();

  return (
    <section className="card">
      <header className="card__header">
        <h2 id="tema-titulo">Aparência</h2>
      </header>
      <div className="tema-escolha" role="radiogroup" aria-labelledby="tema-titulo">
        {OPCOES_DE_TEMA.map((opcao) => (
          <label
            key={opcao.valor}
            className={preferencia === opcao.valor ? 'chip chip--on' : 'chip'}
          >
            <input
              type="radio"
              name="tema"
              value={opcao.valor}
              checked={preferencia === opcao.valor}
              onChange={() => escolher(opcao.valor)}
            />
            {opcao.rotulo}
          </label>
        ))}
      </div>
      <p className="tema-escolha__nota">
        <em>Sistema</em> acompanha o modo claro ou escuro do aparelho. A escolha fica guardada só
        neste navegador.
      </p>
    </section>
  );
}
