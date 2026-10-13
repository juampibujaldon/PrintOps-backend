# PrintOps backend — imagen para Azure Container Apps.
# Etapa 1 (build): JDK completo + Gradle. Etapa 2 (runtime): JRE mínimo sobre Alpine,
# usuario no-root. Solo la etapa final llega al ACR.

ARG AI_AGENT_VERSION=3.7.10
ARG AI_AGENT_SHA256=93a70c8f5d364c7e777f6c4d1b235dba91aef8448bd3fa94359f1d7f3e2eb0ec

# ---------- Etapa 1: build ----------
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src

# Primero solo lo que define las dependencias: se cachea mientras no cambie el build.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon -q dependencies > /dev/null

COPY src src
RUN ./gradlew --no-daemon -q bootJar -x test \
    && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar')" /tmp/app.jar \
    # Jar "extraído": arranca más rápido que el fat jar (factor IX).
    && java -Djarmode=tools -jar /tmp/app.jar extract --destination /out/app \
    && mkdir -p /out/uploads

# Agente de Application Insights (OpenTelemetry): requests, dependencias, logs y
# excepciones a Azure Monitor. Sin APPLICATIONINSIGHTS_CONNECTION_STRING no exporta
# nada y la app arranca igual. Descarga verificada por checksum.
ARG AI_AGENT_VERSION
ARG AI_AGENT_SHA256
# --chmod: ADD desde una URL deja el archivo en 600 (root) y el usuario nonroot no podría leerlo.
ADD --chmod=644 --checksum=sha256:${AI_AGENT_SHA256} \
    https://repo1.maven.org/maven2/com/microsoft/azure/applicationinsights-agent/${AI_AGENT_VERSION}/applicationinsights-agent-${AI_AGENT_VERSION}.jar \
    /out/applicationinsights-agent.jar

# ---------- Etapa 2: runtime ----------
# Temurin JRE sobre Alpine: con `apk upgrade` queda sin CVE críticos ni altos. Las
# distroless de Debian (12 y 13) arrastran paquetes marcados "won't fix" que Grype
# reporta como altos. Solo JRE (sin compilador) y usuario sin privilegios.
FROM eclipse-temurin:21-jre-alpine
RUN apk upgrade --no-cache \
    && addgroup -S -g 65532 nonroot \
    && adduser -S -D -H -u 65532 -G nonroot -s /sbin/nologin nonroot
WORKDIR /app

COPY --from=build /out/app /app
COPY --from=build /out/applicationinsights-agent.jar /agent/applicationinsights-agent.jar
# La app guarda fotos y PDFs en ./uploads: tiene que poder escribir el usuario nonroot.
COPY --from=build --chown=65532:65532 /out/uploads /app/uploads

# Configuración por defecto (12-factor): todo se puede pisar desde el Container App.
# Los secretos (JWT_SECRET, DB_*, connection string) NO van acá.
ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8083 \
    APPLICATIONINSIGHTS_ROLE_NAME=printops-backend \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8083
USER nonroot

# Forma exec: java es el PID 1 y recibe el SIGTERM directo (apagado elegante).
ENTRYPOINT ["java", "-javaagent:/agent/applicationinsights-agent.jar", "-jar", "/app/app.jar"]
