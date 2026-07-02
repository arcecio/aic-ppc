FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradlew .
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN ./gradlew --no-daemon dependencies -q || true
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S aip && adduser -S aip -G aip
COPY --from=build /app/build/libs/*.jar app.jar
RUN mkdir -p /app/storage && chown -R aip:aip /app
USER aip
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
