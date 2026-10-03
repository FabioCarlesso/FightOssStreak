# D52 — O painel de uso é agregado e de ninguém — e é essa restrição, não a tela, que é a decisão

## Justificativa

A #84 gravou e agregou os eventos; sem tela, esse dado só existia para quem abrisse o `psql`. A tela
em si é trabalho, não decisão. O que **é** decisão é a fronteira que ela respeita: o painel lê
`usage_daily` e nunca `usage_event`. A tabela crua tem chave de visita e às vezes `user_id`, e ler
dela daria respostas melhores — funil por pessoa, sessão, retenção entre dias. Cada uma dessas
respostas é a D50 sendo revertida em silêncio, e é por isso que a regra está escrita no javadoc do
serviço, no `docs/privacidade/README.md` e num teste que varre o corpo inteiro da resposta atrás de uma
arroba: quem for acrescentar a consulta daqui a um ano precisa esbarrar nas três. Duas leituras não
vêm do agregado, e nenhuma é o cru: o total de contas sai de `app_user` e "ativas no período" sai da
contagem de `drill_log` — as duas devolvem **número**, nunca linha.

**O que faltava no dado, e entrou como dado.** O funil da issue tem seis degraus e a #84 emitia
quatro; o sexto, "voltou em 7 dias", virou o evento `RETORNO_EM_7_DIAS`, definido como *segundo dia
distinto com drill, dentro de sete dias do primeiro*. Em dias com drill, e não em "abriu de novo",
por dois motivos que se somam: acesso de tela **não é ligável entre dias** — o sal da chave de
visita roda por dia, de propósito —, e voltar para olhar não é voltar para treinar. Contado assim, o
evento sai uma vez só por conta e sem memória própria, que é o mesmo desenho do "primeiro drill". O
perfil pedia navegador e idioma, que o agregador não quebrava; entraram como **duas linhas no
`UsageAggregator`**, sem migration — era exatamente para isso que a `usage_daily` tem formato longo.
Consequência aceita: dia já fechado antes disto continua sem essas duas dimensões, porque contagem
publicada não se reescreve.

**Onde a issue não pôde ser atendida ao pé da letra**: ela pede "nós e telas mais abertos", e o
código do nó **não existe no dado** — a normalização de caminho da D50 troca todo segmento variável
por um marcador, e `/no/{codigo}` é uma linha só. Gravar o código exigiria abrir exceção na regra
que existe para impedir que token de confirmação acabe em tabela de métrica, e trocar "segmento
variável não entra" por "estes segmentos variáveis não entram" é justamente a formulação que falha
no segmento que ninguém lembrou. O painel entrega o ranking de telas e **diz na própria tela** por
que as páginas de nó aparecem juntas.

**Detalhes de desenho que valem registro**: o período termina **ontem**, porque o dia corrente ainda
recebe evento e publicar número que muda depois de lido é pior que não publicá-lo — e a tela
distingue "ninguém apareceu" de "o job ainda não fechou o dia", que dão o mesmo zero; o primeiro
degrau do funil conta **visitantes** e os outros contam **ocorrências**, porque comparar pessoas com
telas abertas daria conversão sem significado; conversão de degrau cujo anterior é zero vem
**nula**, não 0%, porque 0% afirmaria uma queda que ninguém mediu; a cauda dos rankings é **somada
em "outros"** em vez de descartada, senão os números não fecham; e os gráficos são **SVG escrito à
mão**, porque o web tem três dependências de runtime e uma linha com umas barras não justifica a
quarta no bundle de quem abre a landing. O crédito da base DB-IP Lite (CC BY 4.0) que a D50 prometeu
para "quando a tela existir" está no painel, e some quando não há base carregada — creditar base não
usada seria crédito errado.

## Revisar quando

Quando alguma pergunta do painel exigir ligar comportamento a uma pessoa. Aí não é ajuste de tela: é
a D50 sendo revertida, e passa por reescrever `docs/privacidade/README.md` antes de escrever a consulta. Ou
quando a lista de rotas do app crescer a ponto de o ranking de telas virar "outros" — aí o teto de
fatias é o que muda. Ou quando o código do nó passar a valer o preço de uma exceção na normalização
de caminho: nesse dia a saída é normalizar contra a **lista fechada de códigos do currículo**, que é
a mesma disciplina da lista de rotas, e não gravar o segmento cru

---

[Índice das decisões](../README.md)
