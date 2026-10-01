FROM gradle:8.12.1-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew --no-daemon :nodo3-pasarelas:installDist

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/nodo3-pasarelas/build/install/nodo3-pasarelas/ /app/
EXPOSE 10000
ENTRYPOINT ["/app/bin/nodo3-pasarelas"]
