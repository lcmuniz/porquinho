FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/porquinho-backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
