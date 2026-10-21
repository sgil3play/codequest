# syntax=docker/dockerfile:1
#
# Build del backend de CodeQuest.
#
# Contexto de build: la raiz del repositorio (docker compose lo define).
#
#   docker build -f backend/Dockerfile -t codequest-backend .

# ---------------------------------------------------------------------------
# Etapa 1: compilacion
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Las dependencias se resuelven en una capa aparte. Mientras el pom.xml no
# cambie, esta capa sale del cache y el build es mucho mas rapido.
COPY backend/pom.xml backend/mvnw backend/mvnw.cmd ./
COPY backend/.mvn ./.mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline || echo "go-offline incompleto: se resolvera en el siguiente paso"

COPY backend/src ./src
RUN ./mvnw -B -q -DskipTests package

# ---------------------------------------------------------------------------
# Etapa 2: ejecucion
# ---------------------------------------------------------------------------
# Es un JDK y no un JRE a proposito: el motor de ejecucion invoca `javac` para
# compilar el codigo de quien practica, y `javac` no viene en el JRE.
FROM eclipse-temurin:21-jdk-jammy AS runtime

# El codigo de quien practica no necesita privilegios de administrador.
RUN groupadd --system codequest \
    && useradd --system --gid codequest --create-home --home-dir /home/codequest codequest

WORKDIR /app

COPY --from=build /build/target/codequest-*.jar ./app.jar
# El runner localiza el launcher de JUnit en <user.dir>/target/. Se copia ahi
# para que la busqueda funcione sin configuracion adicional.
COPY --from=build /build/target/junit-platform-console-standalone.jar ./target/junit-platform-console-standalone.jar

ENV CODEQUEST_RUNNER_JUNIT_CONSOLE_JAR=/app/target/junit-platform-console-standalone.jar \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    HOME=/home/codequest

EXPOSE 8080
USER codequest

HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
    CMD wget -q -O- http://localhost:8080/actuator/health | grep -q '"status":"UP"'

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
