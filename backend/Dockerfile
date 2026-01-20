FROM clojure:temurin-17-tools-deps

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-client \
    && rm -rf /var/lib/apt/lists/*

COPY deps.edn /app/
RUN clojure -P -M:dev

COPY src /app/src
COPY resources /app/resources
COPY entrypoint.sh /app/entrypoint.sh

RUN chmod +x /app/entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/app/entrypoint.sh"]
