FROM gradle:8.12.1-jdk21 AS build

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        zeroc-ice-compilers \
        libzeroc-ice3.7-java \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

COPY . .

RUN slice2java --version

RUN ./gradlew --no-daemon :nodo3-pasarelas:installDist


FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/nodo3-pasarelas/build/install/nodo3-pasarelas/ /app/

EXPOSE 10000

ENTRYPOINT ["/app/bin/nodo3-pasarelas"]