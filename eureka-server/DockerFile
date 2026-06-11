FROM maven:3.9.11-eclipse-temurin-25 AS builder

WORKDIR /build

COPY . .

RUN mvn -pl eureka-server -am clean package -DskipTests

FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=builder /build/eureka-server/target/*.jar app.jar

EXPOSE 8761

ENTRYPOINT ["java","-jar","app.jar"]