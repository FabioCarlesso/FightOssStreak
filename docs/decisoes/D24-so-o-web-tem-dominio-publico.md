# D24 — Só o `web` tem domínio público; o backend só existe na rede privada

## Justificativa

Sem login (D9), qualquer um com a URL da API escreveria no progresso do único usuário. Tirar o
backend da internet não é defesa em profundidade — é a única defesa que existe hoje, e sai de graça:
o nginx já precisa estar na frente por causa da mesma-origem (D23), então o backend não tem motivo
para ter domínio próprio. Custo: o Swagger não fica acessível em produção, o que é desejável
enquanto não houver controle de acesso

## Revisar quando

Ao introduzir login, ou se algum cliente externo precisar falar com a API direto. HTTPS, controle de
acesso na borda e backup continuam na #4 — **atingido pela D36**: há login, e o backend segue sem
domínio público. A D24 não caiu junto: o portão de aprovação é controle de acesso, não exposição de
superfície

---

[Índice das decisões](../README.md)
