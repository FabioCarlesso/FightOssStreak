# Dado referente à saúde: peso e sensação (D57, #114)

O diário de treino (D56) guarda dois campos que a LGPD trata em regime próprio: **peso** e
**sensação**. São *dado referente à saúde* (art. 5º, II), e esta seção existe porque até a #114 este
documento não usava essa palavra nenhuma vez — o app não tinha o que classificar assim.

Os dois são **opcionais**, e o app funciona inteiro sem eles: sessão sem peso e sem sensação é
sessão válida, e nada na tela cobra o preenchimento.

**O que o app faz com eles: guarda e mostra.** Nada mais.

- **Não interpreta.** Não há meta de peso, faixa saudável, alerta, comparação com a sessão anterior
  nem recomendação de treinar ou descansar. Peso em esporte de luta puxa corte de peso, e sensação
  puxa "treinar ou não"; os dois são conselho, e o FOS não aconselha (D1,
  `docs/produto/disclaimer.md`).
- **Não correlaciona como causa.** A série pode aparecer na tela; a conclusão, não — sensação contra
  recall em vinte sessões é superstição com gráfico.
- **Peso é por sessão, nunca diário.** Pesar no dia do treino é o hábito real; um tracker diário
  seria outro produto, com outra régua de risco.

**Onde eles ficam, e onde não ficam.** Só na sua conta, em `training_session`. Eles **não** entram na
coleta de uso da D50 — nem em `usage_event`, nem em `usage_daily` — e **não** aparecem no painel de
quem administra (D52): o painel lê o agregado e nada nele identifica pessoa, muito menos o peso dela.
Há teste que varre a resposta do painel inteira atrás desses campos e reprova o build se algum
aparecer. Também não entram nas contagens de saúde do site (D54), que contam requisição e não pessoa.

**Quem vê.** Só você. Nem quem administra o app tem tela que mostre o diário de outra conta — a
gestão de usuários (D49) lista conta, e-mail, papel e estado de acesso, e não toca em nada disto.

**Como apagar.** `DELETE /api/me` leva as sessões junto, peso e sensação inclusive, na mesma
transação do resto. Apagar um treino específico não existe hoje: a correção é por edição, e limpar o
campo de peso de uma sessão o remove daquela linha.

Mexer em qualquer uma dessas frases é revisitar a D57 — não é ajuste de tela.
