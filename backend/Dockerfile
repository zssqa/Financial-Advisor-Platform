FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Build: run `mvn clean package -DskipTests=true` in backend/ to create the fat jar
COPY advisor-bootstrap/target/advisor-bootstrap-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
