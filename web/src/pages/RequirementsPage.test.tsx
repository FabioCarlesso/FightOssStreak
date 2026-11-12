import type { GraduationView, RequirementChecklist, TreeView } from '@fos/types';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RequirementsPage } from './RequirementsPage.tsx';

/**
 * Checklist do próximo grau (#196, D78): colar, ligar e marcar. O que importa aqui é que a
 * sugestão de técnica nunca vire vínculo sozinha — ela vem desmarcada e só é gravada na
 * confirmação — e que o requisito sem técnica se marque à mão. A lista é fictícia.
 */
const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    getRequirements: vi.fn<() => Promise<RequirementChecklist>>(),
    getTree: vi.fn<() => Promise<TreeView>>(),
    getGraduation: vi.fn<() => Promise<GraduationView>>(),
    importRequirements:
      vi.fn<(b: { label?: string; text: string }) => Promise<RequirementChecklist>>(),
    updateRequirement: vi.fn<(id: number, p: object) => Promise<RequirementChecklist>>(),
    reorderRequirements: vi.fn<(ids: number[]) => Promise<RequirementChecklist>>(),
    deleteRequirement: vi.fn<(id: number) => Promise<RequirementChecklist>>(),
    deleteRequirements: vi.fn<() => Promise<void>>(),
  },
}));

vi.mock('../api/client.ts', async () => {
  const real = await vi.importActual<typeof import('@fos/api-client')>('@fos/api-client');
  return { api: apiMock, ApiError: real.ApiError };
});

const ARVORE: TreeView = {
  modules: [
    {
      code: 'M1',
      nodes: [
        { code: 'M1.4', title: 'Fuga de montada: upa e elbow escape', positionCodes: ['MONTADA'] },
        { code: 'M7.1', title: 'Kimura', positionCodes: [], aliases: ['chave de ombro'] },
      ],
    },
  ],
  positions: [{ code: 'MONTADA', name: 'Montada', aliases: [], family: 'CONTROLE_POR_CIMA' }],
};

const COM_ITENS: RequirementChecklist = {
  label: 'branca 2º grau',
  done: 0,
  total: 2,
  nodeCodes: [],
  items: [
    { id: 1, order: 0, text: 'Fuga da montada', status: 'PENDENTE', manualDone: false, nodes: [] },
    { id: 2, order: 1, text: 'Tempo de faixa', status: 'PENDENTE', manualDone: false, nodes: [] },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
  apiMock.getTree.mockResolvedValue(ARVORE);
  apiMock.getGraduation.mockResolvedValue({});
});

function renderPage() {
  return render(
    <MemoryRouter>
      <RequirementsPage />
    </MemoryRouter>,
  );
}

describe('RequirementsPage', () => {
  it('cola a lista: mostra a prévia limpa e manda o texto como foi colado', async () => {
    apiMock.getRequirements.mockResolvedValue({ items: [], nodeCodes: [] });
    apiMock.importRequirements.mockResolvedValue(COM_ITENS);
    renderPage();

    await userEvent.type(await screen.findByLabelText(/grupo/i), 'branca 2º grau');
    const campo = screen.getByLabelText(/um requisito por linha/i);
    await userEvent.type(campo, '• Fuga da montada{enter}{enter}- Tempo de faixa');
    expect(screen.getByText(/vira 2 requisitos/i)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /adicionar à checklist/i }));
    expect(apiMock.importRequirements).toHaveBeenCalledWith({
      label: 'branca 2º grau',
      text: '• Fuga da montada\n\n- Tempo de faixa',
    });
    expect(await screen.findByText(/de 2 requisitos concluídos/i)).toBeInTheDocument();
  });

  it('a sugestão vem desmarcada, e só a confirmação grava o vínculo', async () => {
    apiMock.getRequirements.mockResolvedValue(COM_ITENS);
    apiMock.updateRequirement.mockResolvedValue(COM_ITENS);
    renderPage();

    const item = (await screen.findByText('Fuga da montada')).closest('li')!;
    await userEvent.click(within(item).getByRole('button', { name: /ligar técnicas/i }));

    // "montada" sugere M1.4, mas sugerir não é decidir: nada vem marcado.
    const sugestao = within(item).getByRole('checkbox', { name: /M1\.4/ });
    expect(sugestao).not.toBeChecked();
    expect(within(item).getByText(/sugerida/i)).toBeInTheDocument();
    expect(apiMock.updateRequirement).not.toHaveBeenCalled();

    await userEvent.click(sugestao);
    await userEvent.click(within(item).getByRole('button', { name: /confirmar ligação/i }));
    expect(apiMock.updateRequirement).toHaveBeenCalledWith(1, { nodeCodes: ['M1.4'] });
  });

  it('requisito sem técnica se marca à mão', async () => {
    apiMock.getRequirements.mockResolvedValue(COM_ITENS);
    apiMock.updateRequirement.mockResolvedValue(COM_ITENS);
    renderPage();

    const item = (await screen.findByText('Tempo de faixa')).closest('li')!;
    await userEvent.click(within(item).getByRole('checkbox', { name: /feito/i }));
    expect(apiMock.updateRequirement).toHaveBeenCalledWith(2, { manualDone: true });
  });

  it('apagar a checklist pede um segundo toque, sem diálogo do navegador', async () => {
    apiMock.getRequirements.mockResolvedValue(COM_ITENS);
    apiMock.deleteRequirements.mockResolvedValue();
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: /apagar a checklist/i }));
    expect(apiMock.deleteRequirements).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: /^apagar$/i }));
    expect(apiMock.deleteRequirements).toHaveBeenCalledTimes(1);
  });
});
