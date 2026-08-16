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
USER fos

# Só documentação do padrão local. A porta real vem de PORT em runtime — a Railway injeta
# a dela e ignora o EXPOSE.
ENV PORT=8080
EXPOSE 8080

# MaxRAMPercentage porque o default da JVM em container pequeno reserva heap de menos.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
