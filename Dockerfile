FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src

# Copy parent pom + all module poms first (layer-caches dependency downloads)
COPY pom.xml ./
COPY cognis-core/pom.xml           cognis-core/pom.xml
COPY cognis-sdk/pom.xml            cognis-sdk/pom.xml
COPY cognis-cli/pom.xml            cognis-cli/pom.xml
COPY cognis-app/pom.xml            cognis-app/pom.xml
COPY cognis-mcp-server/pom.xml     cognis-mcp-server/pom.xml
COPY cognis-vertical-humanitarian/pom.xml  cognis-vertical-humanitarian/pom.xml
COPY cognis-vertical-sa-agriculture/pom.xml cognis-vertical-sa-agriculture/pom.xml
COPY cognis-vertical-livestock/pom.xml     cognis-vertical-livestock/pom.xml
COPY cognis-vertical-starter/pom.xml       cognis-vertical-starter/pom.xml

# Pre-fetch all dependencies (cache layer)
RUN mvn dependency:go-offline -q --no-transfer-progress || true

# Copy all source trees
COPY docs                                   docs
COPY cognis-core/src                        cognis-core/src
COPY cognis-sdk/src                         cognis-sdk/src
COPY cognis-cli/src                         cognis-cli/src
COPY cognis-app/src                         cognis-app/src
COPY cognis-mcp-server/src                  cognis-mcp-server/src
COPY cognis-vertical-humanitarian/src       cognis-vertical-humanitarian/src
COPY cognis-vertical-sa-agriculture/src     cognis-vertical-sa-agriculture/src
COPY cognis-vertical-livestock/src          cognis-vertical-livestock/src
COPY cognis-vertical-starter/src            cognis-vertical-starter/src

# Build cognis-app and its transitive deps, copy runtime jars
RUN mvn -pl cognis-app -am package dependency:copy-dependencies \
    -DincludeScope=runtime \
    -DoutputDirectory=/src/cognis-app/target/dependency \
    -DskipTests --no-transfer-progress

# Build MCP server
RUN mvn -pl cognis-mcp-server -am package -DskipTests --no-transfer-progress

# ── app-runtime target (used by docker-compose) ───────────────────────────────
FROM eclipse-temurin:21-jre AS app-runtime
ENV HOME=/home/cognis
WORKDIR /app

RUN useradd --create-home --home-dir /home/cognis --shell /bin/bash cognis

COPY --from=build /src/cognis-app/target/cognis-app-0.1.0-SNAPSHOT.jar /app/cognis-app.jar
COPY --from=build /src/cognis-app/target/dependency /app/lib
COPY docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh && chown -R cognis:cognis /app /home/cognis

USER cognis
EXPOSE 8787
ENTRYPOINT ["/app/entrypoint.sh"]

# ── mcp-runtime target (optional MCP server) ──────────────────────────────────
FROM eclipse-temurin:21-jre AS mcp-runtime
ENV HOME=/home/cognis
WORKDIR /app

RUN useradd --create-home --home-dir /home/cognis --shell /bin/bash cognis

COPY --from=build /src/cognis-mcp-server/target/cognis-mcp-server-0.1.0-SNAPSHOT.jar /app/cognis-mcp-server.jar
RUN chown -R cognis:cognis /app /home/cognis

USER cognis
EXPOSE 8791
ENTRYPOINT ["java", "-jar", "/app/cognis-mcp-server.jar"]
