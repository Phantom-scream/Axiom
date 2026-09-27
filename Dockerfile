FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies
COPY src src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre
RUN addgroup --system axiom && adduser --system --ingroup axiom axiom
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER axiom
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD ["sh", "-c", "wget -q -O - http://localhost:8080/actuator/health | grep -q UP"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

