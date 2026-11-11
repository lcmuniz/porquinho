FROM eclipse-temurin:21-jre-alpine
# Usuário sem privilégios: o processo não roda como root
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --chown=app:app target/porquinho-backend-*.jar app.jar
# EXPORT_DIR padrão (./exports) precisa ser gravável pelo usuário app
RUN mkdir exports && chown app:app /app /app/exports
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
