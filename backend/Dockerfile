# Contexto de build: a RAIZ do monorepo, não backend/.
#
#   docker build -f backend/Dockerfile .
#
# Poderia ser ./backend, mas aí a Railway exigiria configurar Root Directory por serviço
# pelo dashboard: `dockerContext` não existe no railway.schema.json, então lá o contexto é
# sempre o diretório raiz do serviço. Um contexto só, igual nos dois ambientes, mantém a
# configuração inteira versionada (mesma lógica da D18).
#
# A mesma imagem roda no Compose local e na Railway. Nada de host, porta ou credencial fixo
# aqui — tudo o que varia entre os dois entra por variável de ambiente (ver README).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# O pom vem antes de src/: assim mudar código não invalida a camada de dependências, que é
# a cara de reconstruir.
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY backend/src/ src/
# Testes ficam com o CI, que roda `./mvnw verify` com o toolchain versionado.
RUN mvn -B -q -DskipTests package

# Base de geolocalização da coleta de uso (#84, D50).
#
# Baixada AQUI, e não versionada nem montada em volume, por eliminação: versionar joga alguns
# MB de dado de terceiro em cada atualização mensal dentro do histórico do git, e volume é
# custo e upload manual num projeto que roda com uma réplica (D22). O download é do estágio de
# BUILD — quem fala com o db-ip.com é a máquina que constrói a imagem, nunca a requisição de
# quem usa o app. A promessa de `docs/11-privacidade.md` continua de pé: o IP de quem navega
# não sai daqui.
#
# NUNCA derruba o build. Sem base o app coleta tudo menos país, que é como dev e CI rodam —
# indisponibilidade de terceiro não pode virar deploy falho. Por isso o `|| true` e o arquivo
# criado vazio no pior caso: `GeoIpDatabase.load` trata ilegível como base vazia e avisa no log.
#
# Mês corrente com recuo para o anterior: a URL do DB-IP carrega AAAA-MM, e um mês fixo aqui
# venceria em silêncio — a virada do mês publica o arquivo novo antes de o antigo sair.
ARG GEOIP=true
RUN set -eux; \
    : > /build/geoip.csv.gz; \
    if [ "$GEOIP" = "true" ]; then \
      for mes in "$(date -u +%Y-%m)" "$(date -u -d '1 month ago' +%Y-%m)"; do \
        url="https://download.db-ip.com/free/dbip-country-lite-$mes.csv.gz"; \
        if curl -fsSL --max-time 120 -o /build/geoip.csv.gz "$url"; then \
          echo "base de geolocalização: $url"; break; \
        fi; \
        echo "base de geolocalização indisponível em $url"; \
      done || true; \
    fi; \
    ls -l /build/geoip.csv.gz

FROM eclipse-temurin:21-jre-alpine AS runtime

# O streak usa LocalDate.now() no servidor: container em UTC faria um drill às 22h de
# Brasília contar como do dia seguinte. tzdata vem explícito para o fuso não depender de
# o base image trazê-lo.
RUN apk add --no-cache tzdata
ENV TZ=America/Sao_Paulo

# Processo não-root: falha na aplicação não vira root dentro do container.
RUN addgroup -S fos && adduser -S -G fos fos
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
# Vazio quando o download não aconteceu (CI, ou db-ip fora do ar). `GeoIpDatabase` trata
# ilegível como base vazia, então a imagem sobe igual nos dois casos.
COPY --from=build /build/geoip.csv.gz geoip.csv.gz
USER fos

# Aponta para o arquivo que a linha acima trouxe. Fica no ENV e não no README para que país
# funcione sem configurar nada na Railway — que é onde a decisão de coletar país já foi
# tomada. Definir a variável no ambiente sobrescreve, inclusive para vazio, que desliga.
#
# Dado de país por DB-IP (https://db-ip.com), sob CC BY 4.0. A licença exige crédito: ele está
# aqui, no README e em docs/11-privacidade.md, e precisa aparecer também no painel da #85
# quando a tela existir.
ENV FOS_USAGE_GEOIP_DATABASE=/app/geoip.csv.gz

# Só documentação do padrão local. A porta real vem de PORT em runtime — a Railway injeta
# a dela e ignora o EXPOSE.
ENV PORT=8080
EXPOSE 8080

# MaxRAMPercentage porque o default da JVM em container pequeno reserva heap de menos.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
