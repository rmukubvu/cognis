FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src

COPY pom.xml ./
COPY cognis-core/pom.xml cognis-core/pom.xml
COPY cognis-cli/pom.xml cognis-cli/pom.xml
COPY cognis-app/pom.xml cognis-app/pom.xml
COPY docs docs
COPY cognis-core/src cognis-core/src
COPY cognis-cli/src cognis-cli/src
COPY cognis-app/src cognis-app/src

RUN mvn -pl cognis-app -am package dependency:copy-dependencies \
    -DincludeScope=runtime \
    -DoutputDirectory=/src/cognis-app/target/dependency \
    -DskipTests

FROM eclipse-temurin:21-jre
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
