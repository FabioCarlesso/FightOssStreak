import { type FormEvent, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api/client.ts';
import { AuthCard } from './AuthCard.tsx';
import { mensagemDeErro } from './erros.ts';

/**
 * Esqueci minha senha.
 *
 * A tela de sucesso é o ponto todo: ela diz **"se existe uma conta"**, no condicional, e isso não é
 * cautela de redação — é a mesma indistinção que o backend mantém (D47). Confirmar que o endereço
 * tem conta transformaria esta página, que é pública e não pede nada, em consulta de quem usa o
 * app.
 */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [enviado, setEnviado] = useState(false);
  const [falha, setFalha] = useState<string | null>(null);

  async function pedir(event: FormEvent) {
    event.preventDefault();
    setEnviando(true);
    setFalha(null);
    try {
      await api.requestPasswordReset(email);
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
        <p>
          Se existe uma conta com senha para <strong>{email}</strong>, o link de redefinição acabou
          de sair. Ele vale <strong>1 hora</strong> e funciona uma vez só.
        </p>
        <p>
          Se você entra pelo Google, não há senha a redefinir — é só usar o botão do Google na tela
          de entrada.
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
    <AuthCard titulo="Esqueci minha senha">
      <p>Diga o endereço da conta e enviamos um link para escolher uma senha nova.</p>
      <form className="login__email" onSubmit={(event) => void pedir(event)}>
        <label htmlFor="esquecida-email">E-mail</label>
        <input
          id="esquecida-email"
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
            {enviando ? 'Enviando…' : 'Enviar o link'}
          </button>
          <Link className="linklike" to="/entrar">
            Voltar
          </Link>
        </div>
      </form>
    </AuthCard>
  );
}
