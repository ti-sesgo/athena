# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8085 9095
LABEL prometheus.scrape="true" prometheus.port="9095" prometheus.path="/actuator/prometheus"
ENTRYPOINT ["java", "-jar", "app.jar"]
