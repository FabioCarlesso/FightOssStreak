import type { NodeDetail, PinnedNote, QuizResult, QuizSubmission } from '@fos/types';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DemoModeProvider } from '../state/DemoModeProvider.tsx';
import { NodePage } from './NodePage.tsx';

/**
 * Trocar de nó sem sair da rota `/no/:code` é o que a demonstração transforma em fluxo principal:
 * percorrer 46 nós em sequência. O React reaproveita a mesma `NodePage` nessa troca, então tudo
 * que ela guarda — o dado carregado, o estado dos formulários — precisa acompanhar o parâmetro da
 * URL. Não acompanhar tem duas consequências, e as duas gravam ou mentem sobre gravação.
 *
 * O cliente de API é mockado no módulo: nenhum teste toca a rede.
 */
const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    getNode: vi.fn<(code: string) => Promise<NodeDetail>>(),
    submitQuiz: vi.fn<(code: string, submission: QuizSubmission) => Promise<QuizResult>>(),
    savePinnedNote: vi.fn<(code: string, note: string) => Promise<PinnedNote>>(),
  },
}));

vi.mock('../api/client.ts', async () => {
  const real = await vi.importActual<typeof import('@fos/api-client')>('@fos/api-client');
  return { api: apiMock, ApiError: real.ApiError };
});

/** Mesmo canal que o toggle da árvore usa para atravessar a navegação (`state/demoMode.ts`). */
const CHAVE_DEMO = 'fos.demo';

const DISPONIVEL: NodeDetail = {
  code: 'M0.2',
  title: 'Quedas seguras',
  moduleCode: 'M0',
  moduleTitle: 'Fundamentos',
  belt: 'BRANCA',
  status: 'AVAILABLE',
  concept: 'Distribuir o impacto em vez de concentrá-lo.',
  safetyNotice: 'Pratique somente em academia.',
  recentDrills: [
    { drilledOn: '2026-08-16', recall: 'HARD', note: 'entrei com a cabeça baixa' },
    { drilledOn: '2026-08-12', recall: 'OK' },
  ],
  quiz: [
    {
      id: 1,
      prompt: 'Qual é o princípio central de uma queda segura?',
      options: [
        { id: 11, label: 'Distribuir o impacto' },
        { id: 12, label: 'Cair mais rápido' },
      ],
    },
  ],
};

const BLOQUEADO: NodeDetail = {
  code: 'M0.3',
  title: 'Como tocar (tap)',
  moduleCode: 'M0',
  moduleTitle: 'Fundamentos',
  belt: 'BRANCA',
  status: 'LOCKED',
  unlockRule: 'ALL',
  concept: 'Tocar cedo é o que permite treinar amanhã.',
  safetyNotice: 'Pratique somente em academia.',
  pinnedNote: 'anotação de um nó que ficou bloqueado',
  prereqs: [{ code: 'M0.2', title: 'Quedas seguras', completed: false }],
  quiz: [
    {
      id: 2,
      prompt: 'Quando tocar?',
      options: [
        { id: 21, label: 'Antes de doer' },
        { id: 22, label: 'Depois que travar' },
      ],
    },
  ],
};

function renderNode() {
  return render(
    <DemoModeProvider>
      <MemoryRouter initialEntries={['/no/M0.2']}>
        {/* Fora das rotas de propósito: navegar por ele troca só o parâmetro, como na árvore. */}
        <Link to="/no/M0.3">ir para o próximo nó</Link>
        <Routes>
          <Route path="/no/:code" element={<NodePage />} />
        </Routes>
      </MemoryRouter>
    </DemoModeProvider>,
  );
}

function irParaOProximoNo() {
  return userEvent.click(screen.getByRole('link', { name: /ir para o próximo nó/i }));
}

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  apiMock.getNode.mockImplementation((code) =>
    Promise.resolve(code === 'M0.2' ? DISPONIVEL : BLOQUEADO),
  );
  apiMock.savePinnedNote.mockImplementation((code, note) =>
    Promise.resolve({ nodeCode: code, note: note.trim() || undefined }),
  );
});

describe('NodePage', () => {
  it('o resultado do quiz não atravessa a troca de nó', async () => {
    // Em demonstração o estrago é maior: o nó de destino está bloqueado e a tela acabou de
    // prometer que nada é gravado — exibir "nó concluído" ali diria exatamente o contrário.
    sessionStorage.setItem(CHAVE_DEMO, 'on');
    apiMock.submitQuiz.mockResolvedValue({
      score: 100,
      correctCount: 1,
      totalQuestions: 1,
      passed: true,
      passingScore: 70,
      feedback: [],
    });

    renderNode();
    await userEvent.click(await screen.findByRole('radio', { name: /distribuir o impacto/i }));
    await userEvent.click(screen.getByRole('button', { name: /responder/i }));
    expect(await screen.findByText(/nó concluído/i)).toBeInTheDocument();

    await irParaOProximoNo();

    expect(await screen.findByText(/este nó continua bloqueado/i)).toBeInTheDocument();
    expect(screen.queryByText(/nó concluído/i)).not.toBeInTheDocument();
    expect(screen.getByText(/quando tocar/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /responder/i })).toBeDisabled();
  });

  it('recarregar o mesmo nó preserva o resultado do quiz', async () => {
    // A contrapartida da regra acima: `onDone` recarrega o nó para atualizar status e SRS, e se
    // isso desmontasse o formulário a nota sumiria no instante em que aparece.
    apiMock.submitQuiz.mockResolvedValue({
      score: 100,
      correctCount: 1,
      totalQuestions: 1,
      passed: true,
      passingScore: 70,
      feedback: [],
    });

    renderNode();
    await userEvent.click(await screen.findByRole('radio', { name: /distribuir o impacto/i }));
    await userEvent.click(screen.getByRole('button', { name: /responder/i }));

    await waitFor(() => expect(apiMock.getNode).toHaveBeenCalledTimes(2));
    expect(screen.getByText(/nó concluído/i)).toBeInTheDocument();
  });

  it('enquanto o nó novo não chega, o anterior sai da tela inteiro', async () => {
    // O registro de drill é o motivo: ele carrega o código do nó que está na tela. Deixar o nó
    // anterior visível sob a URL nova abriria uma janela em que "Treinei isso hoje" grava no nó
    // errado — e o nó de destino ainda apareceria como destravado.
    let entregarBloqueado: (detail: NodeDetail) => void = () => {};
    apiMock.getNode.mockImplementation((code) =>
      code === 'M0.2'
        ? Promise.resolve(DISPONIVEL)
        : new Promise((resolve) => {
            entregarBloqueado = resolve;
          }),
    );

    renderNode();
    expect(await screen.findByRole('button', { name: /treinei isso hoje/i })).toBeInTheDocument();

    await irParaOProximoNo();

    expect(screen.getByText(/carregando nó/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /treinei isso hoje/i })).not.toBeInTheDocument();
    expect(screen.queryByText(/quedas seguras/i)).not.toBeInTheDocument();

    entregarBloqueado(BLOQUEADO);

    expect(await screen.findByText(/nó bloqueado/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /treinei isso hoje/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /responder/i })).not.toBeInTheDocument();
  });

  it('a anotação fixada é gravada e aparece sem esperar a recarga do nó', async () => {
    renderNode();
    await userEvent.click(await screen.findByRole('button', { name: /^anotar$/i }));
    await userEvent.type(screen.getByLabelText(/sua anotação/i), 'cotovelo colado');
    await userEvent.click(screen.getByRole('button', { name: /salvar/i }));

    expect(apiMock.savePinnedNote).toHaveBeenCalledWith('M0.2', 'cotovelo colado');

    // O nó recarrega, mas a resposta mockada ainda é a antiga (sem `pinnedNote`). O texto tem que
    // continuar na tela mesmo assim: é a janela em que se olha para conferir se salvou.
    expect(await screen.findByText('cotovelo colado')).toBeInTheDocument();
    await waitFor(() => expect(apiMock.getNode).toHaveBeenCalledTimes(2));
    expect(screen.getByText('cotovelo colado')).toBeInTheDocument();
  });

  it('clicar em "Anotar" leva o foco para o campo', async () => {
    // O botão clicado é substituído pelo campo. Sem mover o foco junto, o que se digita em
    // seguida vai para o `<body>` e se perde — foi assim que o defeito apareceu na revisão:
    // 33 caracteres digitados logo após o clique, campo continuou vazio.
    renderNode();
    await userEvent.click(await screen.findByRole('button', { name: /^anotar$/i }));

    const campo = screen.getByLabelText(/sua anotação/i);
    expect(campo).toHaveFocus();

    await userEvent.keyboard('cotovelo colado');
    expect(campo).toHaveValue('cotovelo colado');
  });

  it('editar anotação existente deixa o caret no fim do texto', async () => {
    // `focus()` num textarea preenchido deixa o caret no começo, e aí digitar escreve ao contrário
    // do esperado. Editar uma anotação quase sempre é acrescentar.
    apiMock.getNode.mockResolvedValue({ ...DISPONIVEL, pinnedNote: 'joelho antes do pé' });

    renderNode();
    await userEvent.click(await screen.findByRole('button', { name: /editar anotação/i }));

    const campo = screen.getByLabelText(/sua anotação/i);
    expect(campo).toHaveFocus();

    await userEvent.keyboard(' e cotovelo colado');
    expect(campo).toHaveValue('joelho antes do pé e cotovelo colado');
  });

  it('em demonstração a anotação é leitura e nada é gravado', async () => {
    // Mesmo risco que a D31 já pegou no quiz e no drill: a demonstração não grava, e um campo
    // editável aqui contrariaria a faixa que a própria tela acabou de exibir.
    sessionStorage.setItem(CHAVE_DEMO, 'on');

    renderNode();
    await irParaOProximoNo();

    expect(await screen.findByText(/este nó continua bloqueado/i)).toBeInTheDocument();
    expect(screen.getByText('anotação de um nó que ficou bloqueado')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^anotar$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /editar anotação/i })).not.toBeInTheDocument();
    expect(apiMock.savePinnedNote).not.toHaveBeenCalled();
  });

  it('conceito de parágrafo único continua um só bloco de texto', async () => {
    renderNode();

    const conceito = await screen.findByText('Distribuir o impacto em vez de concentrá-lo.');
    expect(conceito.tagName).toBe('P');
    expect(screen.getAllByText(/./, { selector: '.node__concept' })).toHaveLength(1);
  });

  it('linha em branco no conceito vira parágrafo separado na tela', async () => {
    apiMock.getNode.mockResolvedValue({
      ...DISPONIVEL,
      concept:
        'A primeira habilidade não é escapar.\n\nO mecanismo é a hierarquia: pescoço, cotovelos, ar.\n\nO erro comum é explodir cedo.',
    });

    renderNode();

    const paragrafos = await screen.findAllByText(/./, { selector: '.node__concept' });
    expect(paragrafos).toHaveLength(3);
    expect(paragrafos[0]).toHaveTextContent('A primeira habilidade não é escapar.');
    expect(paragrafos[1]).toHaveTextContent('O mecanismo é a hierarquia: pescoço, cotovelos, ar.');
    expect(paragrafos[2]).toHaveTextContent('O erro comum é explodir cedo.');
    // A linha em branco em si não deve sobrar como texto literal em nenhum parágrafo.
    paragrafos.forEach((paragrafo) => expect(paragrafo.textContent).not.toMatch(/\n/));
  });

  it('o histórico de anotações some junto com o registro de drill em nó bloqueado', async () => {
    renderNode();
    expect(await screen.findByText('entrei com a cabeça baixa')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /suas anotações/i })).toBeInTheDocument();

    await irParaOProximoNo();

    expect(await screen.findByText(/nó bloqueado/i)).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /suas anotações/i })).not.toBeInTheDocument();
    expect(screen.queryByText('entrei com a cabeça baixa')).not.toBeInTheDocument();
  });
});
