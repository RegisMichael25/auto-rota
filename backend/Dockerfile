# Build: compila o jar com o Maven Wrapper (dependências em camada própria, para cache).
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/auto-rota-backend-*.jar app.jar

# Runtime: só a JRE, usuário sem privilégios.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/app.jar app.jar
USER app

ENV SPRING_PROFILES_ACTIVE=prod
# Render Free tem 512 MB: heap limitado à fração da memória do container e GC serial.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError"
# A porta vem de PORT (Render define a sua); 8080 quando ausente.
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
