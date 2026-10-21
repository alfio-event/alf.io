#
# Alf.io dockerfile.
# Thanks to:
# - https://mjg123.github.io/2018/11/05/alpine-jdk11-images.html
# - https://august.nagro.us/small-java.html
#

# JDK used to build the runtime image.
ARG JDK_IMAGE=eclipse-temurin:27-alpine
ARG GC_OPTS="-XX:+UseParallelGC"

FROM ${JDK_IMAGE} AS jdk
ARG GC_OPTS
COPY alfio-boot.jar .
RUN "$JAVA_HOME/bin/jlink" --compress=zip-6 --strip-java-debug-attributes --no-header-files --no-man-pages \
    --module-path "$JAVA_HOME/jmods" \
    # see https://docs.oracle.com/en/java/javase/11/security/oracle-providers.html#GUID-9224B90B-7B2F-41F9-BB96-C0A1B6A0FEAA
    --add-modules java.desktop,java.logging,java.sql,java.management,java.naming,jdk.unsupported,jdk.crypto.ec,java.net.http,jdk.localedata,java.instrument \
    --include-locales en,it,es,nl,fr,de,ro,pt,tr,pl,da,bg,sv,cs \
    --output /jlinked

RUN /jlinked/bin/java -Xshare:dump -XX:+UseSerialGC -version
# Leyden AOT cache (JDK 25+): classes loaded and linked during the training run, plus their JIT profiles.
# The training run starts the application context without a database (app-cds profile) and exits once it is refreshed.
RUN "$JAVA_HOME/bin/java" -Djarmode=tools -jar alfio-boot.jar extract --destination /cds && \
    (cd /cds && /jlinked/bin/java $GC_OPTS -Xmx1g -XX:AOTCacheOutput=alfio.aot \
        -Dspring.profiles.active=app-cds -Dspring.context.exit=onRefresh -jar alfio-boot.jar)


FROM alpine:3.24
ARG GC_OPTS
LABEL org.opencontainers.image.source=https://github.com/alfio-event/alf.io

ENV LANG en_US.UTF-8
ENV ALFIO_JAVA_OPTS=""
ENV ALFIO_PERFORMANCE_OPTS="-Dspring.jmx.enabled=false -Dlog4j2.disableJmx=true"
ENV ALFIO_GC_OPTS="${GC_OPTS}"

RUN addgroup -S alfio  \
    && adduser -h /home/alfio -u 1001 -G alfio -S alfio  \
    && apk update  \
    && apk add --update ttf-dejavu  \
    && rm -rf /var/cache/apk/* \
    && mkdir /home/alfio/app

COPY --from=jdk /jlinked /opt/jdk/
COPY --chown=alfio --from=jdk /cds/ /home/alfio/app/

USER 1001

# Define working directory.
WORKDIR /home/alfio/app

CMD /opt/jdk/bin/java $ALFIO_GC_OPTS $ALFIO_JAVA_OPTS $ALFIO_PERFORMANCE_OPTS -XX:AOTCache=alfio.aot -jar alfio-boot.jar

EXPOSE 8080
