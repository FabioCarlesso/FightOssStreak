import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError, api } from '../../api/client.ts';
import { useAsync } from '../../state/useAsync.ts';
import { AuthCard } from './AuthCard.tsx';
import { mensagemDeErro } from './erros.ts';

/**
 * Entrar: e-mail e senha, ou um provedor.
 *
 * Esta tela era um portão e virou uma porta (D47). Antes ela dizia "o app é de uso pessoal", pedia
 * acesso e mandava esperar a liberação do autor; nada disso existe mais, e nenhuma palavra sobre
 * fila, pedido ou aprovação sobrou aqui — não por limpeza, mas porque instrução que descreve um
 * fluxo que não existe é pior do que instrução nenhuma.
 *
 * Os botões de provedor são âncoras, e não `fetch`: o fluxo OAuth é uma navegação de página inteira
 * para o provedor e de volta. Chamada assíncrona seria bloqueada e não levaria a lugar nenhum.
 */
export function SignInPage() {
  const providers = useAsync(() => api.getAuthProviders(), []);
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [entrando, setEntrando] = useState(false);
  const [falha, setFalha] = useState<string | null>(null);
  const [naoConfirmado, setNaoConfirmado] = useState(false);

  const lista = providers.data?.providers ?? [];
  const senhaHabilitada = providers.data?.passwordEnabled ?? false;
  const nenhumaEntrada = !providers.loading && !providers.error && lista.length === 0;

  async function entrar(event: FormEvent) {
    event.preventDefault();
    setEntrando(true);
    setFalha(null);
    setNaoConfirmado(false);
    try {
      await api.signInWithPassword(email, senha);
      // Navegação do router, e não `window.location`: a sessão já está no cookie, e recarregar a
      // página inteira só descartaria o app que acabou de carregar.
      navigate('/hoje', { replace: true });
    } catch (cause) {
      // A senha estava certa e o e-mail não foi confirmado: é o único caso em que a tela tem uma
      // saída concreta a oferecer, e escondê-la atrás de "não conseguimos entrar" deixaria a
      // pessoa presa com a credencial correta na mão.
      if (cause instanceof ApiError && cause.isEmailUnverified) {
        setNaoConfirmado(true);
      } else {
        setFalha(mensagemDeErro(cause));
      }
    } finally {
      setEntrando(false);
    }
  }

  if (naoConfirmado) {
    return <ConfirmacaoPendente email={email} onVoltar={() => setNaoConfirmado(false)} />;
  }

  return (
    <AuthCard titulo="Entrar no FightOssStreak">
      {providers.loading && <p className="empty">Carregando…</p>}

      {providers.error && (
        <>
          <p className="gate__error">{providers.error.message}</p>
          <button type="button" onClick={providers.reload}>
            Tentar de novo
          </button>
        </>
      )}

      {senhaHabilitada && (
        <form className="login__email" onSubmit={(event) => void entrar(event)}>
          <label htmlFor="entrar-email">E-mail</label>
          <input
            id="entrar-email"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="voce@exemplo.com"
          />
          <label htmlFor="entrar-senha">Senha</label>
          <input
            id="entrar-senha"
            type="password"
            autoComplete="current-password"
            required
            value={senha}
            onChange={(event) => setSenha(event.target.value)}
          />
          {falha && <p className="gate__error">{falha}</p>}
          <div className="gate__actions">
            <button type="submit" disabled={entrando || !email || !senha}>
              {entrando ? 'Entrando…' : 'Entrar'}
            </button>
          </div>
        </form>
      )}

      {lista.length > 0 && (
        <div className="login__providers">
          {lista.map((provider) => (
            <a key={provider.id} className="login__provider" href={provider.authorizationUrl}>
              Entrar com {provider.label}
            </a>
          ))}
        </div>
      )}

      {nenhumaEntrada && !senhaHabilitada && (
        <p className="gate__error">
          Nenhuma forma de entrada está configurada neste ambiente. Defina as credenciais descritas
          no README para habilitar o login.
        </p>
      )}

      <p className="login__alternativa">
        {senhaHabilitada && (
          <>
            <Link to="/cadastrar">Criar uma conta</Link> ·{' '}
            <Link to="/senha/esquecida">Esqueci minha senha</Link>
            <br />
          </>
        )}
        <Link to="/?ver=apresentacao">O que é o FightOssStreak</Link>
      </p>
    </AuthCard>
  );
}

/**
 * Senha certa, endereço ainda não confirmado.
 *
 * O reenvio fica aqui, e não numa mensagem de erro, porque é a ação inteira desta tela: a pessoa
 * tem a credencial e falta só o clique no link. O `email` já está preenchido — pedi-lo de novo
 * seria burocracia sobre alguém que acabou de digitá-lo.
 */
function ConfirmacaoPendente({ email, onVoltar }: { email: string; onVoltar: () => void }) {
  const [enviando, setEnviando] = useState(false);
  const [enviado, setEnviado] = useState(false);
  const [falha, setFalha] = useState<string | null>(null);

  async function reenviar() {
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

  return (
    <AuthCard titulo="Falta confirmar seu e-mail">
      <p>
        A senha está certa, mas <strong>{email}</strong> ainda não foi confirmado. O link de
        confirmação foi para essa caixa quando você se cadastrou — ele vale 24 horas e funciona uma
        vez.
      </p>
      {enviado ? (
        <p>Enviamos outro link agora. Se não chegar em alguns minutos, confira a caixa de spam.</p>
      ) : (
        <>
          {falha && <p className="gate__error">{falha}</p>}
          <div className="gate__actions">
            <button type="button" onClick={() => void reenviar()} disabled={enviando}>
              {enviando ? 'Enviando…' : 'Reenviar o link'}
            </button>
            <button type="button" onClick={onVoltar}>
              Voltar
            </button>
          </div>
        </>
      )}
    </AuthCard>
  );
}
