import type { ReactNode } from 'react';

/**
 * A moldura das telas de fronteira: entrar, cadastrar, confirmar, redefinir.
 *
 * Reaproveita o card do portão (`.gate`) porque são exatamente isso — fronteira, não produto. Um
 * visual próprio aqui faria a antessala parecer o app, e a primeira impressão do FightOssStreak
 * seria um formulário.
 */
export function AuthCard({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <div className="gate">
      <div className="gate__card">
        <h2>{titulo}</h2>
        {children}
      </div>
    </div>
  );
}
