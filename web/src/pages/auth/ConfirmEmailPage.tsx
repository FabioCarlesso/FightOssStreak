import { type FormEvent, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../../api/client.ts';
import { AuthCard } from './AuthCard.tsx';
import { mensagemDeErro } from './erros.ts';

/**
 * O que aconteceu quando o link de confirmação **não** deu certo.
 *
 * Quando dá certo, esta tela não aparece: o backend abre a sessão e redireciona direto para
 * `/hoje`. Ela existe para os três desfechos ruins, e existe separada da tela de entrada porque as
 * ações são diferentes em cada um — **vencido** pede o endereço e reenvia, **usado** manda entrar
 * (a conta já está confirmada), **inválido** não tem o que oferecer além de pedir outro. Amontoar
 * os três numa faixa de erro em cima do formulário de login esconderia a saída em dois deles.
 */
export function ConfirmEmailPage() {
  const [params] = useSearchParams();
  const motivo = params.get('erro') ?? 'invalido';

  if (motivo === 'usado') {
    return (
      <AuthCard titulo="Este link já foi usado">
        <p>
          Seu e-mail já está confirmado — cada link funciona uma vez só, e este já cumpriu o papel
          dele. É só entrar.
        </p>
        <div className="gate__actions">
          <Link className="login__provider" to="/entrar">
            Entrar
          </Link>
        </div>
      </AuthCard>
    );
  }

  if (motivo === 'vencido') {
    return (
      <Reenviar
        titulo="Este link venceu"
        explicacao="O link de confirmação vale 24 horas, e o prazo deste passou. Diga o endereço da conta e enviamos outro."
      />
    );
  }

  return (
    <Reenviar
      titulo="Link inválido"
      explicacao="Este endereço não corresponde a nenhum link de confirmação. Confira se ele foi copiado inteiro do e-mail — alguns clientes quebram URLs longas em duas linhas. Se preferir, peça um link novo."
    />
  );
}

/**
 * Pede o endereço e reenvia.
 *
 * Pergunta o e-mail em vez de deduzi-lo do token: o token já não vale (é por isso que estamos aqui),
 * então não há de onde tirar o endereço — e adivinhar seria pedir ao backend que revelasse de quem
 * é a conta a quem só tem uma URL vencida.
 */
function Reenviar({ titulo, explicacao }: { titulo: string; explicacao: string }) {
  const [email, setEmail] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [enviado, setEnviado] = useState(false);
  const [falha, setFalha] = useState<string | null>(null);

  async function reenviar(event: FormEvent) {
    event.preventDefault();
    setEnviando(true);
    setFalha(null);
    try {
      await api.resendVerification(email);
      setEnviado(true);
    } catch (cause) {
      setFalha(mensagemDeErro(cause));
    } finally {
      setEnviando(false);
    }
  }

  if (enviado) {
    return (
      <AuthCard titulo="Confira seu e-mail">
        {/* No condicional, como em toda rota pública que fala de e-mail: a resposta do backend é a
            mesma para cadastro pendente, já confirmado e inexistente. */}
        <p>
          Se existe um cadastro à espera de confirmação para <strong>{email}</strong>, o link novo
          acabou de sair. Ele vale 24 horas e funciona uma vez só.
        </p>
        <div className="gate__actions">
          <Link className="linklike" to="/entrar">
            Voltar para a entrada
          </Link>
        </div>
      </AuthCard>
    );
  }

  return (
    <AuthCard titulo={titulo}>
      <p>{explicacao}</p>
      <form className="login__email" onSubmit={(event) => void reenviar(event)}>
        <label htmlFor="confirmar-email">E-mail do cadastro</label>
        <input
          id="confirmar-email"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          placeholder="voce@exemplo.com"
        />
        {falha && <p className="gate__error">{falha}</p>}
        <div className="gate__actions">
          <button type="submit" disabled={enviando || !email}>
            {enviando ? 'Enviando…' : 'Reenviar o link'}
          </button>
          <Link className="linklike" to="/entrar">
            Voltar
          </Link>
        </div>
      </form>
    </AuthCard>
  );
}
