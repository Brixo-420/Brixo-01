# syntax=docker/dockerfile:1

# ---- Etapa de build ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cachear dependencias en su propia capa: solo se re-descargan si cambia el pom.xml
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- Etapa de ejecución ----
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN useradd --system --uid 1001 appuser
COPY --from=build /app/target/*.jar app.jar
USER appuser

EXPOSE 8080

# Render inyecta PORT en runtime; server.port no está fijado en application.properties
# asi que se pasa como -D en vez de tocar el archivo de config.
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75.0 -Dserver.port=${PORT:-8080} -jar app.jar"]
