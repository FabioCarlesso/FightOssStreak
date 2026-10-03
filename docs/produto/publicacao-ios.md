# Publicação iOS — Desafios e Decisão

> Em andamento desde a D67: o épico mobile (#138 a #144) segue, e a publicação é a #144.

## Desafios de publicar no iOS
- **Precisa de um Mac** para compilar via Xcode — não é possível gerar o `.ipa` em Linux. Solução: build na nuvem via **EAS Build** (Expo) ou Codemagic.
- **Apple Developer Program**: US$ 99/ano.
- **Review manual**: cerca de 90% das submissões recebem decisão em 24h e 98% em 48h, mas rejeições por guideline reiniciam o ciclo.
- **Exigências de privacidade**: política de privacidade obrigatória, privacy labels de coleta de dados, **exclusão de conta obrigatória se houver login**, transparência sobre uso de IA externa.
- Desde 28/04/2026, apps enviados precisam usar o SDK do iOS/iPadOS 26 ou mais recente.

## Riscos específicos deste app na review

| Risco | Guideline afetada | Mitigação |
|---|---|---|
| Conteúdo majoritariamente em embed de YouTube | *Minimum functionality* — apps que são só um wrapper de conteúdo de terceiros são rejeitados | O valor do app está na árvore de progressão, quiz e SRS, não no vídeo. Garantir que isso seja evidente na UI e na descrição da loja |
| Ensino de técnicas de combate | *Physical harm* | Disclaimers visíveis (ver `docs/produto/disclaimer.md`) e aceite no primeiro uso |
| Login sem rota de deleção | Rejeição certa | Implementar deleção de conta junto com o login, não depois |

## RN tem limitação para publicação?
Não. A review da Apple avalia o binário e o comportamento do app, independentemente do framework (RN, Flutter ou nativo).

## Decisão
Validar tudo primeiro na web (ver `docs/produto/mvp-web.md`) era a regra da D4, e a D66 a confirmou com os critérios do MVP abaixo da meta. A D67 a revisitou: o mobile começa porque aprender a desenvolver e publicar para as duas plataformas é objetivo próprio, sem que os critérios do MVP afrouxem.
