FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build
COPY pom.xml ./
COPY subscription-service/pom.xml ./subscription-service/
COPY payment-router/pom.xml ./payment-router/
COPY authorize-net-service/pom.xml ./authorize-net-service/
COPY paddle-service/pom.xml ./paddle-service/
RUN mvn dependency:go-offline -pl authorize-net-service -am --no-transfer-progress -q
COPY authorize-net-service/src ./authorize-net-service/src
RUN mvn package -pl authorize-net-service -DskipTests --no-transfer-progress

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/authorize-net-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
