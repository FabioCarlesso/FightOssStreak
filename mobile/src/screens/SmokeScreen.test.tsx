import { render, screen } from '@testing-library/react-native';

import type { Api } from '../api/client';
import { SmokeScreen } from './SmokeScreen';

/** A tela de fumaça com a API trocada por uma resposta fixa: nada aqui toca a rede. */
function apiQueResponde(resposta: Awaited<ReturnType<Api['getAuthProviders']>>): Api {
  return { getAuthProviders: () => Promise.resolve(resposta) };
}

describe('SmokeScreen', () => {
  it('roda a regra de @fos/domain no React Native', async () => {
    await render(
      <SmokeScreen
        api={apiQueResponde({ providers: [], demoEnabled: false, passwordEnabled: false })}
        apiUrl="http://api.test"
        today="2026-10-05"
      />,
    );

    expect(screen.getByTestId('streak')).toHaveTextContent('currentStreak de 3 dias seguidos = 3');
  });

  it('mostra as entradas do app que a API pública informa', async () => {
    await render(
      <SmokeScreen
        api={apiQueResponde({
          providers: [],
          demoEnabled: false,
          passwordEnabled: true,
          mobileProviders: ['google'],
        })}
        apiUrl="http://api.test"
      />,
    );

    expect(await screen.findByText('API no ar. Entradas do app: senha, google')).toBeOnTheScreen();
  });

  it('diz o que configurar quando falta EXPO_PUBLIC_API_URL, sem chamar a API', async () => {
    const getAuthProviders = jest.fn();

    await render(<SmokeScreen api={{ getAuthProviders }} apiUrl="" />);

    expect(screen.getByTestId('providers')).toHaveTextContent(/Defina EXPO_PUBLIC_API_URL/);
    expect(getAuthProviders).not.toHaveBeenCalled();
  });

  it('mostra o erro quando a API não responde', async () => {
    await render(
      <SmokeScreen
        api={{ getAuthProviders: () => Promise.reject(new Error('Network request failed')) }}
        apiUrl="http://api.test"
      />,
    );

    expect(
      await screen.findByText('A API não respondeu: Network request failed'),
    ).toBeOnTheScreen();
  });
});
