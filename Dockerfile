# Build: compila el jar ejecutable (build/libs/app.jar) sin correr tests.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q > /dev/null
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# Runtime: solo el JRE y el jar.
FROM eclipse-temurin:26.0.1_8-jre
WORKDIR /app
COPY --from=build /app/build/libs/app.jar app.jar
RUN mkdir -p uploads
ENV SPRING_PROFILES_ACTIVE=prod
# Escucha en IPv4 e IPv6: la red privada de Railway (*.railway.internal) usa IPv6. El front
# llega por ahí y así el backend ve la IP real del cliente (rate limit por IP).
ENV SERVER_ADDRESS=::
EXPOSE 8083
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
