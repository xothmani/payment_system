FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build
COPY pom.xml ./
COPY subscription-service/pom.xml ./subscription-service/
COPY payment-router/pom.xml ./payment-router/
COPY authorize-net-service/pom.xml ./authorize-net-service/
COPY paddle-service/pom.xml ./paddle-service/
RUN mvn dependency:go-offline -pl paddle-service -am --no-transfer-progress -q
COPY paddle-service/src ./paddle-service/src
RUN mvn package -pl paddle-service -DskipTests --no-transfer-progress

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/paddle-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
