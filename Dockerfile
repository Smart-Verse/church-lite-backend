# Stage 1: Build do uber-jar com Maven Wrapper e JDK 25
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY .gonthera ./.gonthera
COPY src ./src
RUN ./mvnw clean package -DskipTests -B && \
    cp target/*.jar /app/app.jar

# Stage 2: Runtime leve com JRE 25
FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /app/app.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
