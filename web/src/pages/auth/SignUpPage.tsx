import { type FormEvent, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api/client.ts';
import { useAsync } from '../../state/useAsync.ts';
import { AuthCard } from './AuthCard.tsx';
import { mensagemDeErro } from './erros.ts';
import { REGRA_SENHA, SENHA_MINIMA, forcaDaSenha, problemaNaSenha } from './senha.ts';

/**
 * Criar conta.
 *
 * Duas coisas que a tela faz e valem ser ditas. **A regra da senha aparece antes do erro**, embaixo
 * do campo: descobrir o mínimo depois de preencher o formulário inteiro é o atrito que a #82 veio
 * tirar. **E a confirmação da senha é conferida aqui**, e não no servidor — o backend recebe uma
 * senha só, porque "as duas não batem" é problema do formulário, não do domínio.
 *
 * O que a tela **não** faz: dizer se o endereço já tem conta. O backend responde igual para os dois
 * casos (D47) e a tela repete a indistinção; quem já tem conta descobre pelo e-mail que chega na
 * própria caixa, que é o único lugar onde essa informação não vaza para ninguém.
 */
export function SignUpPage() {
  const providers = useAsync(() => api.getAuthProviders(), []);
  const [nome, setNome] = useState('');
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [confirmacao, setConfirmacao] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [falha, setFalha] = useState<string | null>(null);
  const [cadastrado, setCadastrado] = useState(false);

  const senhaHabilitada = providers.data?.passwordEnabled ?? false;
  const problema = problemaNaSenha(senha);
  const naoBatem = confirmacao.length > 0 && senha !== confirmacao;
  const podeEnviar =
    !enviando && email.length > 0 && senha.length >= SENHA_MINIMA && !problema && !naoBatem;

  async function cadastrar(event: FormEvent) {
    event.preventDefault();
    setEnviando(true);
    setFalha(null);
    try {
      await api.signUp(email, senha, nome);
      setCadastrado(true);
    } catch (cause) {
      setFalha(mensagemDeErro(cause));
    } finally {
      setEnviando(false);
    }
  }

  if (cadastrado) {
    return <ConfirmeSeuEmail email={email} />;
  }

  if (!providers.loading && !senhaHabilitada) {
    return <CadastroIndisponivel />;
  }

  return (
    <AuthCard titulo="Criar sua conta">
      <p>
        A conta é criada agora e confirmada por um link que vai para o seu e-mail. Não há fila nem
        aprovação — o link é o único passo entre você e o app.
      </p>

      <form className="login__email" onSubmit={(event) => void cadastrar(event)}>
        {/* Opcional de propósito: é como o app vai te chamar, não uma identidade a conferir. Sem
            ele o cabeçalho mostra o endereço — funciona, e é feio. */}
        <label htmlFor="cadastro-nome">Seu nome (opcional)</label>
        <input
          id="cadastro-nome"
          type="text"
          autoComplete="name"
          value={nome}
          onChange={(event) => setNome(event.target.value)}
          placeholder="Como o app vai te chamar"
        />

        <label htmlFor="cadastro-email">E-mail</label>
        <input
          id="cadastro-email"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          placeholder="voce@exemplo.com"
        />

        <label htmlFor="cadastro-senha">Senha</label>
        <input
          id="cadastro-senha"
          type="password"
          autoComplete="new-password"
          required
          value={senha}
          onChange={(event) => setSenha(event.target.value)}
          aria-describedby="cadastro-senha-regra"
        />
        <p id="cadastro-senha-regra" className="senha__regra">
          {REGRA_SENHA}
        </p>
        {senha.length > 0 && <MedidorDeForca senha={senha} />}
        {problema && <p className="gate__error">{problema}</p>}

        <label htmlFor="cadastro-confirmacao">Repita a senha</label>
        <input
          id="cadastro-confirmacao"
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
            {enviando ? 'Criando…' : 'Criar conta'}
          </button>
        </div>
      </form>

      <p className="login__alternativa">
        Já tem conta? <Link to="/entrar">Entrar</Link>
      </p>
    </AuthCard>
  );
}

/**
 * Barra de força, e o que ela deliberadamente não é.
 *
 * Ela **não reprova** nada além do mínimo de comprimento: exigir maiúscula, número e símbolo não
 * mede força — produz `Senha@2026` — e é a razão de a política do backend seguir o NIST SP 800-63B.
 * A barra informa; quem decide é quem digita.
 */
function MedidorDeForca({ senha }: { senha: string }) {
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

/**
 * "Confirme seu e-mail" — a tela entre o cadastro e a conta.
 *
 * O reenvio tem contador de espera. O freio de verdade é do backend (5 por hora, por endereço e por
 * IP); o contador existe para a UI não **convidar** ao clique repetido, que é como alguém queima o
 * próprio limite e fica sem entender por que parou de funcionar.
 */
function ConfirmeSeuEmail({ email }: { email: string }) {
  const [enviando, setEnviando] = useState(false);
  const [espera, setEspera] = useState(0);
  const [falha, setFalha] = useState<string | null>(null);

  async function reenviar() {
    setEnviando(true);
    setFalha(null);
    try {
      await api.resendVerification(email);
      setEspera(60);
      const id = setInterval(() => {
        setEspera((restante) => {
          if (restante <= 1) {
            clearInterval(id);
            return 0;
          }
          return restante - 1;
        });
      }, 1000);
    } catch (cause) {
      setFalha(mensagemDeErro(cause));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <AuthCard titulo="Confirme seu e-mail">
      <p>
        Enviamos um link para <strong>{email}</strong>. Abra esse link para confirmar o endereço e
        entrar — ele vale <strong>24 horas</strong> e funciona uma vez só.
      </p>
      <p>Não chegou? Confira a caixa de spam antes de pedir outro.</p>
      {falha && <p className="gate__error">{falha}</p>}
      <div className="gate__actions">
        <button type="button" onClick={() => void reenviar()} disabled={enviando || espera > 0}>
          {enviando ? 'Enviando…' : espera > 0 ? `Reenviar em ${espera}s` : 'Reenviar o link'}
        </button>
        <Link className="linklike" to="/entrar">
          Já confirmei, quero entrar
        </Link>
      </div>
    </AuthCard>
  );
}

/**
 * Ambiente sem provedor de envio de e-mail.
 *
 * É o caso de dev e do CI, onde a aplicação sobe sem segredo nenhum de propósito. Como o cadastro
 * *é* o e-mail de confirmação, oferecer o formulário aqui criaria contas que ninguém consegue
 * confirmar — daí a tela dizer o que houve em vez de falhar no envio.
 */
function CadastroIndisponivel() {
  return (
    <AuthCard titulo="Cadastro indisponível neste ambiente">
      <p>
        Criar conta com e-mail e senha depende de um provedor de envio de e-mail configurado, e este
        ambiente não tem nenhum — o link de confirmação não teria como sair, e a conta ficaria
        pendurada para sempre.
      </p>
      <p>
        Se você está rodando o app localmente, o README explica quais variáveis habilitam o envio.
      </p>
      <div className="gate__actions">
        <Link className="linklike" to="/entrar">
          Voltar para a entrada
        </Link>
      </div>
    </AuthCard>
  );
}
