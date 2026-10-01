FROM gradle:8.12.1-jdk21 AS build

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        zeroc-ice-compilers \
        libzeroc-ice3.7-java \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

COPY . .

RUN slice2java --version

RUN ./gradlew --no-daemon :nodo2-backend:installDist


FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/nodo2-backend/build/install/nodo2-backend/ /app/

EXPOSE 8081 10001

ENTRYPOINT ["/app/bin/nodo2-backend"]