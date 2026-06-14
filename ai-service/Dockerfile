FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY pom.xml .
COPY eureka-server/pom.xml eureka-server/
COPY api-gateway/pom.xml api-gateway/
COPY auth-service/pom.xml auth-service/
COPY url-service/pom.xml url-service/
COPY analytics-service/pom.xml analytics-service/
COPY ai-service/pom.xml ai-service/

COPY .mvn .mvn
COPY mvnw .

COPY ai-service/src ai-service/src
RUN ./mvnw -pl ai-service -am package -DskipTests

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/ai-service/target/*.jar app.jar

EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]