const STORAGE_KEY = 'fos.visitou';

/**
 * Marca de "esta pessoa já entrou no app".
 *
 * Existe por causa de um conflito real: a landing precisa ser a primeira tela de quem recebe o
 * link, e o app precisa continuar a um toque de distância para quem o usa todo dia — e os dois
 * disputam a mesma rota `/`. A marca resolve sem inventar conta: visitante novo vê a apresentação,
 * quem já entrou vai direto para a agenda do dia.
 *
 * `localStorage`, e não `sessionStorage` como o modo demonstração (D31): aqui a intenção é
 * exatamente sobreviver ao fechamento do navegador. E é gravada só depois do portão de aceite —
 * quem parou no aviso e desistiu ainda não entrou em lugar nenhum.
 */
export function markAppVisited(): void {
  try {
    localStorage.setItem(STORAGE_KEY, 'sim');
  } catch {
    // Armazenamento bloqueado: a landing volta a aparecer na raiz, e só. Nada quebra.
  }
}

export function hasVisitedApp(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'sim';
  } catch {
    return false;
  }
}
