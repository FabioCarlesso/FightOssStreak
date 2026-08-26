import type { AuthProviders, LinkStatus } from '@fos/types';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ConfirmEmailPage } from './ConfirmEmailPage.tsx';
import { ForgotPasswordPage } from './ForgotPasswordPage.tsx';
import { ResetPasswordPage } from './ResetPasswordPage.tsx';
import { SignInPage } from './SignInPage.tsx';
import { SignUpPage } from './SignUpPage.tsx';

/**
 * As telas de fronteira: cadastrar, entrar, confirmar e redefinir (#82).
 *
 * Mesmo critério da D29 que o `DisclaimerGate` e o `AuthGate` já seguem: o que se testa não é
 * layout, é o que a tela **diz e faz** em cada desfecho. Três riscos concretos guiam os casos:
 *
 * <ol>
 *   <li>uma mensagem revelar se um endereço tem conta — o backend responde igual de propósito, e
 *       desfazer isso no cliente esconderia o vazamento onde ninguém procura;
 *   <li>a regra da senha só aparecer depois do erro, que é o atrito que a #82 veio tirar;
 *   <li>link vencido virar erro genérico, escondendo o botão de reenviar.
 * </ol>
 *
 * O cliente de API é mockado no módulo: nenhum teste toca a rede.
 */
const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    getAuthProviders: vi.fn<() => Promise<AuthProviders>>(),
    signUp: vi.fn<(email: string, senha: string, nome?: string) => Promise<void>>(),
    signInWithPassword: vi.fn<(email: string, senha: string) => Promise<void>>(),
    resendVerification: vi.fn<(email: string) => Promise<void>>(),
    requestPasswordReset: vi.fn<(email: string) => Promise<void>>(),
    checkVerificationLink: vi.fn<(token: string) => Promise<LinkStatus>>(),
    confirmEmail: vi.fn<(token: string) => Promise<void>>(),
    checkPasswordResetLink: vi.fn<(token: string) => Promise<LinkStatus>>(),
    resetPassword: vi.fn<(token: string, senha: string) => Promise<void>>(),
  },
}));

vi.mock('../../api/client.ts', async () => {
  const real = await vi.importActual<typeof import('@fos/api-client')>('@fos/api-client');
  return { api: apiMock, ApiError: real.ApiError };
});

const { ApiError } = await vi.importActual<typeof import('@fos/api-client')>('@fos/api-client');

const APP = 'a agenda de hoje';

function renderEm(rota: string) {
  return render(
    <MemoryRouter initialEntries={[rota]}>
      <Routes>
        <Route path="/entrar" element={<SignInPage />} />
        <Route path="/cadastrar" element={<SignUpPage />} />
        <Route path="/confirmar-email/:token" element={<ConfirmEmailPage />} />
        <Route path="/confirmar-email" element={<ConfirmEmailPage />} />
        <Route path="/senha/esquecida" element={<ForgotPasswordPage />} />
        <Route path="/senha/redefinir/:token" element={<ResetPasswordPage />} />
        <Route path="/hoje" element={<p>{APP}</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  apiMock.getAuthProviders.mockResolvedValue({
    providers: [
      { id: 'google', label: 'Google', authorizationUrl: '/api/oauth2/authorization/google' },
    ],
    demoEnabled: false,
    passwordEnabled: true,
  });
  apiMock.signUp.mockResolvedValue(undefined);
  apiMock.signInWithPassword.mockResolvedValue(undefined);
  apiMock.resendVerification.mockResolvedValue(undefined);
  apiMock.requestPasswordReset.mockResolvedValue(undefined);
  apiMock.resetPassword.mockResolvedValue(undefined);
  apiMock.checkPasswordResetLink.mockResolvedValue({ valido: true });
  apiMock.checkVerificationLink.mockResolvedValue({ valido: true });
  apiMock.confirmEmail.mockResolvedValue(undefined);
});

describe('entrar', () => {
  it('oferece senha, provedor, criar conta e recuperar — e nada sobre pedir acesso', async () => {
    renderEm('/entrar');

    await screen.findByLabelText(/^senha$/i);
    expect(screen.getByLabelText(/e-mail/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /entrar com google/i })).toHaveAttribute(
      'href',
      '/api/oauth2/authorization/google',
    );
    expect(screen.getByRole('link', { name: /criar uma conta/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /esqueci minha senha/i })).toBeInTheDocument();

    // O vocabulário da fila saiu inteiro: instrução que descreve um fluxo inexistente é pior que
    // instrução nenhuma.
    expect(document.body.textContent).not.toMatch(/pedir acesso|aprovaç|liberaç|aguard/i);
  });

  it('entrar com a senha certa leva ao app', async () => {
    renderEm('/entrar');

    await userEvent.type(await screen.findByLabelText(/e-mail/i), 'ana@example.test');
    await userEvent.type(screen.getByLabelText(/^senha$/i), 'tatame-quarta-feira');
    await userEvent.click(screen.getByRole('button', { name: /^entrar$/i }));

    expect(apiMock.signInWithPassword).toHaveBeenCalledWith(
      'ana@example.test',
      'tatame-quarta-feira',
    );
    expect(await screen.findByText(APP)).toBeInTheDocument();
  });

  it('credencial errada não diz se o e-mail existe', async () => {
    apiMock.signInWithPassword.mockRejectedValue(
      new ApiError(401, 'credencial_invalida', 'E-mail ou senha não conferem.'),
    );

    renderEm('/entrar');
    await userEvent.type(await screen.findByLabelText(/e-mail/i), 'ana@example.test');
    await userEvent.type(screen.getByLabelText(/^senha$/i), 'senha-que-nao-e-a-dela');
    await userEvent.click(screen.getByRole('button', { name: /^entrar$/i }));

    expect(await screen.findByText(/e-mail ou senha não conferem/i)).toBeInTheDocument();
    // A frase é sobre o par. "Não existe conta com este e-mail" viraria consulta de quem usa o app.
    expect(document.body.textContent).not.toMatch(/não (existe|há) conta|conta não encontrada/i);
  });

  it('senha certa e e-mail não confirmado oferece reenviar, sem pedir o endereço de novo', async () => {
    apiMock.signInWithPassword.mockRejectedValue(
      new ApiError(403, 'email_nao_verificado', 'Confirme seu e-mail para entrar.'),
    );

    renderEm('/entrar');
    await userEvent.type(await screen.findByLabelText(/e-mail/i), 'ana@example.test');
    await userEvent.type(screen.getByLabelText(/^senha$/i), 'tatame-quarta-feira');
    await userEvent.click(screen.getByRole('button', { name: /^entrar$/i }));

    await screen.findByRole('heading', { name: /falta confirmar seu e-mail/i });
    await userEvent.click(screen.getByRole('button', { name: /reenviar o link/i }));

    expect(apiMock.resendVerification).toHaveBeenCalledWith('ana@example.test');
    expect(await screen.findByText(/enviamos outro link/i)).toBeInTheDocument();
  });

  it('conta bloqueada por tentativas pede para esperar, e não repete a tentativa', async () => {
    apiMock.signInWithPassword.mockRejectedValue(
      new ApiError(429, 'muitas_tentativas', 'Tentativas demais.'),
    );

    renderEm('/entrar');
    await userEvent.type(await screen.findByLabelText(/e-mail/i), 'ana@example.test');
    await userEvent.type(screen.getByLabelText(/^senha$/i), 'tatame-quarta-feira');
    await userEvent.click(screen.getByRole('button', { name: /^entrar$/i }));

    expect(await screen.findByText(/espere alguns minutos/i)).toBeInTheDocument();
  });
});

describe('cadastrar', () => {
  it('diz a regra da senha antes do erro, e barra a senha curta no cliente', async () => {
    renderEm('/cadastrar');

    // A regra está na tela desde o primeiro instante, não depois de uma submissão recusada.
    expect(await screen.findByText(/pelo menos 12 caracteres/i)).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText(/^e-mail$/i), 'ana@example.test');
    await userEvent.type(screen.getByLabelText(/^senha$/i), 'curta123');

    expect(screen.getByText(/faltam 4 caractere/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /criar conta/i })).toBeDisabled();
    expect(apiMock.signUp).not.toHaveBeenCalled();
  });

  it('senhas diferentes não enviam nada', async () => {
    renderEm('/cadastrar');

    await userEvent.type(await screen.findByLabelText(/^e-mail$/i), 'ana@example.test');
    await userEvent.type(screen.getByLabelText(/^senha$/i), 'tatame-quarta-feira');
    await userEvent.type(screen.getByLabelText(/repita a senha/i), 'tatame-quinta-feira');

    expect(screen.getByText(/as duas senhas não são iguais/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /criar conta/i })).toBeDisabled();
  });

  it('cadastro certo leva à tela de confirmação, com reenvio', async () => {
    renderEm('/cadastrar');

    await userEvent.type(await screen.findByLabelText(/seu nome/i), 'Ana');
    await userEvent.type(screen.getByLabelText(/^e-mail$/i), 'ana@example.test');
    await userEvent.type(screen.getByLabelText(/^senha$/i), 'tatame-quarta-feira');
    await userEvent.type(screen.getByLabelText(/repita a senha/i), 'tatame-quarta-feira');
    await userEvent.click(screen.getByRole('button', { name: /criar conta/i }));

    expect(apiMock.signUp).toHaveBeenCalledWith('ana@example.test', 'tatame-quarta-feira', 'Ana');
    expect(
      await screen.findByRole('heading', { name: /confirme seu e-mail/i }),
    ).toBeInTheDocument();
    expect(screen.getByText(/24 horas/i)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /reenviar o link/i }));
    expect(apiMock.resendVerification).toHaveBeenCalledWith('ana@example.test');
    // Contador de espera: a UI não pode convidar ao clique repetido, que é como alguém queima o
    // próprio limite sem entender por que parou de funcionar.
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /reenviar em \d+s/i })).toBeDisabled(),
    );
  });

  it('sem envio de e-mail configurado, diz isso em vez de mostrar formulário quebrado', async () => {
    apiMock.getAuthProviders.mockResolvedValue({
      providers: [],
      demoEnabled: false,
      passwordEnabled: false,
    });

    renderEm('/cadastrar');

    expect(
      await screen.findByRole('heading', { name: /cadastro indisponível neste ambiente/i }),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText(/^senha$/i)).not.toBeInTheDocument();
  });
});

describe('recuperação de senha', () => {
  it('responde a mesma coisa para e-mail cadastrado e não cadastrado', async () => {
    renderEm('/senha/esquecida');
    await userEvent.type(await screen.findByLabelText(/e-mail/i), 'ana@example.test');
    await userEvent.click(screen.getByRole('button', { name: /enviar o link/i }));
    const comConta = await screen.findByText(/se existe uma conta com senha/i);
    const textoComConta = comConta.textContent ?? '';

    renderEm('/senha/esquecida');
    const campos = await screen.findAllByLabelText(/e-mail/i);
    await userEvent.type(campos[campos.length - 1]!, 'ninguem@example.test');
    const botoes = screen.getAllByRole('button', { name: /enviar o link/i });
    await userEvent.click(botoes[botoes.length - 1]!);
    const semConta = (await screen.findAllByText(/se existe uma conta com senha/i)).at(-1);

    // A única diferença legítima é o endereço ecoado; a promessa é condicional nos dois casos.
    expect(textoComConta.replace('ana@example.test', 'X')).toEqual(
      (semConta?.textContent ?? '').replace('ninguem@example.test', 'X'),
    );
  });

  it('link válido mostra o formulário e troca a senha', async () => {
    renderEm('/senha/redefinir/abc123');

    await userEvent.type(await screen.findByLabelText(/^senha nova$/i), 'guarda-fechada-2026');
    await userEvent.type(screen.getByLabelText(/repita a senha nova/i), 'guarda-fechada-2026');
    await userEvent.click(screen.getByRole('button', { name: /trocar a senha/i }));

    expect(apiMock.resetPassword).toHaveBeenCalledWith('abc123', 'guarda-fechada-2026');
    expect(await screen.findByRole('heading', { name: /senha trocada/i })).toBeInTheDocument();
  });

  it('link vencido oferece pedir outro, e não um erro genérico', async () => {
    apiMock.checkPasswordResetLink.mockResolvedValue({ valido: false, motivo: 'vencido' });

    renderEm('/senha/redefinir/abc123');

    expect(await screen.findByRole('heading', { name: /este link venceu/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /pedir outro link/i })).toBeInTheDocument();
    expect(screen.queryByLabelText(/^senha nova$/i)).not.toBeInTheDocument();
  });

  it('link já usado manda entrar, porque a senha já foi trocada', async () => {
    apiMock.checkPasswordResetLink.mockResolvedValue({ valido: false, motivo: 'usado' });

    renderEm('/senha/redefinir/abc123');

    expect(
      await screen.findByRole('heading', { name: /este link já foi usado/i }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /^entrar$/i })).toBeInTheDocument();
  });
});

describe('link de confirmação', () => {
  it('abrir o link não confirma nada — quem confirma é o clique', async () => {
    renderEm('/confirmar-email/tok-123');

    expect(
      await screen.findByRole('button', { name: /confirmar meu e-mail/i }),
    ).toBeInTheDocument();
    // O ponto do caso: até aqui, nada foi consumido. É o que impede um varredor de link de
    // queimar a confirmação antes de a pessoa clicar.
    expect(apiMock.confirmEmail).not.toHaveBeenCalled();
    expect(apiMock.checkVerificationLink).toHaveBeenCalledWith('tok-123');

    await userEvent.click(screen.getByRole('button', { name: /confirmar meu e-mail/i }));

    expect(apiMock.confirmEmail).toHaveBeenCalledWith('tok-123');
    expect(await screen.findByText(APP)).toBeInTheDocument();
  });

  it('vencido pede o endereço e reenvia', async () => {
    apiMock.checkVerificationLink.mockResolvedValue({ valido: false, motivo: 'vencido' });
    renderEm('/confirmar-email/tok-vencido');

    expect(await screen.findByRole('heading', { name: /este link venceu/i })).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText(/e-mail do cadastro/i), 'ana@example.test');
    await userEvent.click(screen.getByRole('button', { name: /reenviar o link/i }));

    expect(apiMock.resendVerification).toHaveBeenCalledWith('ana@example.test');
    // No condicional: a resposta do backend é a mesma para cadastro pendente, confirmado e
    // inexistente, e a tela não pode desfazer isso.
    expect(await screen.findByText(/se existe um cadastro/i)).toBeInTheDocument();
  });

  it('já usado manda entrar, sem oferecer reenvio', async () => {
    apiMock.checkVerificationLink.mockResolvedValue({ valido: false, motivo: 'usado' });
    renderEm('/confirmar-email/tok-usado');

    expect(
      await screen.findByRole('heading', { name: /este link já foi usado/i }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /^entrar$/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /reenviar/i })).not.toBeInTheDocument();
  });

  it('inválido explica o que pode ter acontecido e oferece outro link', async () => {
    apiMock.checkVerificationLink.mockResolvedValue({ valido: false, motivo: 'invalido' });
    renderEm('/confirmar-email/tok-qualquer');

    expect(await screen.findByRole('heading', { name: /link inválido/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /reenviar o link/i })).toBeInTheDocument();
  });
});
