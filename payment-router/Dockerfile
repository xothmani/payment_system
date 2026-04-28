FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build
COPY pom.xml ./
COPY subscription-service/pom.xml ./subscription-service/
COPY payment-router/pom.xml ./payment-router/
COPY authorize-net-service/pom.xml ./authorize-net-service/
COPY paddle-service/pom.xml ./paddle-service/
RUN mvn dependency:go-offline -pl payment-router -am --no-transfer-progress -q
COPY payment-router/src ./payment-router/src
RUN mvn package -pl payment-router -DskipTests --no-transfer-progress

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/payment-router/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
