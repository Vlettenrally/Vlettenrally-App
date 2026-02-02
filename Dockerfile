FROM quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-21

USER root
RUN mkdir -p /src && chown -R quarkus:quarkus /src
USER quarkus

COPY --chown=quarkus:quarkus api/src/main /src/src/main
COPY --chown=quarkus:quarkus api/pom.xml /src/pom.xml
COPY --chown=quarkus:quarkus api/mvnw /src/mvnw
COPY --chown=quarkus:quarkus api/.mvn /src/.mvn

WORKDIR /src
RUN ./mvnw package -B -ntp -Dnative

FROM quay.io/quarkus/ubi9-quarkus-micro-image:2.0
WORKDIR /work/
RUN chown 1001 /work \
    && chmod "g+rwX" /work \
    && chown 1001:root /work
COPY --chown=1001:root --chmod=0755 --from=0 /src/target/*-runner /work/application
EXPOSE 8080
USER 1001
ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]
