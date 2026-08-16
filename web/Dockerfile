# Contexto de build: a RAIZ do monorepo, não web/.
#
#   docker build -f web/Dockerfile .
#
# @fos/domain, @fos/api-client e @fos/types exportam TypeScript direto de src/ (não têm
# dist/), então o Vite compila o código dos workspaces junto com o do web. Sem os pacotes
# irmãos no contexto, o build quebra na resolução dos imports.

# Node 22 é o que o CI usa (.github/workflows/web.yml) — build da imagem e build do CI
# rodam no mesmo toolchain de propósito.
FROM node:22-alpine AS build
WORKDIR /app

# Manifests antes do código: mexer em src/ não invalida a camada do `npm ci`.
COPY package.json package-lock.json ./
COPY shared/domain/package.json shared/domain/
COPY shared/api-client/package.json shared/api-client/
COPY shared/types/package.json shared/types/
COPY web/package.json web/
RUN npm ci

COPY shared/ shared/
COPY web/ web/
RUN npm run build --workspace @fos/web

FROM nginx:1.27-alpine AS runtime

ENV TZ=America/Sao_Paulo

COPY --from=build /app/web/dist /usr/share/nginx/html

# A imagem oficial do nginx roda envsubst sobre /etc/nginx/templates/*.template no start e
# escreve o resultado em /etc/nginx/conf.d/. É o que permite `listen $PORT` — nginx não lê
# variável de ambiente sozinho.
COPY web/nginx.conf.template /etc/nginx/templates/default.conf.template

# Sem filtro, o envsubst do entrypoint substituiria qualquer variável presente no ambiente,
# inclusive as do próprio nginx ($uri, $host, $request_uri) caso alguma coincida de nome.
ENV NGINX_ENVSUBST_FILTER="^(PORT|BACKEND_ORIGIN|NGINX_RESOLVER)$"

# Defaults do Compose local. Na Railway os três são sobrescritos pelo ambiente, sem rebuild.
ENV PORT=80
ENV BACKEND_ORIGIN=http://backend:8080
ENV NGINX_RESOLVER="127.0.0.11 ipv6=off"

EXPOSE 80
