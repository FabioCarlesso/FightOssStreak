import { type FormEvent, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../api/client.ts';
import { useAsync } from '../../state/useAsync.ts';
import { AuthCard } from './AuthCard.tsx';
import { mensagemDeErro } from './erros.ts';
import { REGRA_SENHA, SENHA_MINIMA, forcaDaSenha, problemaNaSenha } from './senha.ts';

/**
 * Escolher a senha nova, a partir do link recebido por e-mail.
 *
 * A tela **consulta** o link antes de mostrar o formulário, e a consulta não o gasta: consumir na
 * abertura queimaria o link em qualquer pré-carregamento do navegador ou do cliente de e-mail — e a
 * pessoa cairia num "link inválido" sem nunca ter digitado nada.
 *
 * Os três motivos de recusa levam a lugares diferentes, e é por isso que o backend os distingue:
 * **vencido** oferece pedir outro, **usado** manda entrar (a senha já foi trocada), **inválido** não
 * tem o que oferecer. Um "link inválido" para os três esconderia a saída em dois deles.
 */
export function ResetPasswordPage() {
  const { token = '' } = useParams();
  const link = useAsync(() => api.checkPasswordResetLink(token), [token]);
  const [senha, setSenha] = useState('');
  const [confirmacao, setConfirmacao] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [pronto, setPronto] = useState(false);
  const [falha, setFalha] = useState<string | null>(null);

  const problema = problemaNaSenha(senha);
  const naoBatem = confirmacao.length > 0 && senha !== confirmacao;
  const podeEnviar =
    !enviando && senha.length >= SENHA_MINIMA && !problema && !naoBatem && confirmacao.length > 0;

  async function redefinir(event: FormEvent) {
    event.preventDefault();
    setEnviando(true);
    setFalha(null);
    try {
      await api.resetPassword(token, senha);
      setPronto(true);
    } catch (cause) {
      setFalha(mensagemDeErro(cause));
    } finally {
      setEnviando(false);
    }
  }

  if (pronto) {
    return (
      <AuthCard titulo="Senha trocada">
        <p>
          Pronto. Qualquer sessão que estivesse aberta nesta conta foi encerrada — inclusive a de
          quem tivesse entrado com a senha antiga.
        </p>
        <div className="gate__actions">
          <Link className="login__provider" to="/entrar">
            Entrar com a senha nova
          </Link>
        </div>
      </AuthCard>
    );
  }

  if (link.loading && !link.data) {
    return <p className="empty">Carregando…</p>;
  }

  if (link.error) {
    return (
      <AuthCard titulo="Não foi possível conferir o link">
        <p className="gate__error">{link.error.message}</p>
        <div className="gate__actions">
          <button type="button" onClick={link.reload}>
            Tentar de novo
          </button>
        </div>
      </AuthCard>
    );
  }

  if (!link.data?.valido) {
    return <LinkRecusado motivo={link.data?.motivo ?? 'invalido'} />;
  }

  return (
    <AuthCard titulo="Escolha uma senha nova">
      <form className="login__email" onSubmit={(event) => void redefinir(event)}>
        <label htmlFor="redefinir-senha">Senha nova</label>
        <input
          id="redefinir-senha"
          type="password"
          autoComplete="new-password"
          required
          value={senha}
          onChange={(event) => setSenha(event.target.value)}
          aria-describedby="redefinir-senha-regra"
        />
        <p id="redefinir-senha-regra" className="senha__regra">
          {REGRA_SENHA}
        </p>
        {senha.length > 0 && <Forca senha={senha} />}
        {problema && <p className="gate__error">{problema}</p>}

        <label htmlFor="redefinir-confirmacao">Repita a senha nova</label>
        <input
          id="redefinir-confirmacao"
          type="password"
          autoComplete="new-password"
          required
          value={confirmacao}
          onChange={(event) => setConfirmacao(event.target.value)}
        />
        {naoBatem && <p className="gate__error">As duas senhas não são iguais.</p>}

        {falha && <p className="gate__error">{falha}</p>}
        <div className="gate__actions">
          <button type="submit" disabled={!podeEnviar}>
            {enviando ? 'Trocando…' : 'Trocar a senha'}
          </button>
        </div>
      </form>
    </AuthCard>
  );
}

function Forca({ senha }: { senha: string }) {
  const forca = forcaDaSenha(senha);
  const nivel = { curta: 1, ok: 2, boa: 3, ótima: 4 }[forca];
  return (
    <p className={`senha__forca senha__forca--${nivel}`}>
      <span className="senha__barra" aria-hidden="true">
        <span style={{ width: `${nivel * 25}%` }} />
      </span>
      <span>Força: {forca}</span>
    </p>
  );
}

function LinkRecusado({ motivo }: { motivo: string }) {
  if (motivo === 'vencido') {
    return (
      <AuthCard titulo="Este link venceu">
        <p>
          O link de redefinição vale <strong>1 hora</strong>, e o prazo deste passou. Peça outro — o
          novo chega na mesma caixa.
        </p>
        <div className="gate__actions">
          <Link className="login__provider" to="/senha/esquecida">
            Pedir outro link
          </Link>
        </div>
      </AuthCard>
    );
  }

  if (motivo === 'usado') {
    return (
      <AuthCard titulo="Este link já foi usado">
        <p>
          A senha desta conta já foi trocada com ele — cada link funciona uma vez só. Entre com a
          senha nova; se não foi você quem trocou, peça outro link agora.
        </p>
        <div className="gate__actions">
          <Link className="login__provider" to="/entrar">
            Entrar
          </Link>
          <Link className="linklike" to="/senha/esquecida">
            Pedir outro link
          </Link>
        </div>
      </AuthCard>
    );
  }

  return (
    <AuthCard titulo="Link inválido">
      <p>
        Este endereço não corresponde a nenhum link de redefinição. Confira se ele foi copiado
        inteiro do e-mail — alguns clientes quebram URLs longas em duas linhas.
      </p>
      <div className="gate__actions">
        <Link className="login__provider" to="/senha/esquecida">
          Pedir um link novo
        </Link>
      </div>
    </AuthCard>
  );
}
