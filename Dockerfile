FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY build/libs/push-server.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
