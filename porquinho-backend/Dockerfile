# Build: o jar sai daqui, com o Maven wrapper do projeto (não precisa de jar pronto em target/)
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Dependências numa camada própria: só baixa de novo quando o pom.xml muda
RUN ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests && cp target/porquinho-backend-*.jar app.jar

FROM eclipse-temurin:21-jre-alpine
# Usuário sem privilégios: o processo não roda como root
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /build/app.jar app.jar
# EXPORT_DIR padrão (./exports) precisa ser gravável pelo usuário app; no compose vira volume nomeado
RUN mkdir exports && chown app:app /app /app/exports
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
