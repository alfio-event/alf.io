#
# Alf.io dockerfile.
# Thanks to:
# - https://mjg123.github.io/2018/11/05/alpine-jdk11-images.html
# - https://august.nagro.us/small-java.html
#

FROM azul/zulu-openjdk-alpine:17 AS zulu
COPY alfio-boot.jar .
RUN "$JAVA_HOME/bin/jlink" --compress=2 --strip-java-debug-attributes --no-header-files --no-man-pages \
    --module-path "$JAVA_HOME/jmods" \
    # see https://docs.oracle.com/en/java/javase/11/security/oracle-providers.html#GUID-9224B90B-7B2F-41F9-BB96-C0A1B6A0FEAA
    --add-modules java.desktop,java.logging,java.sql,java.management,java.naming,jdk.unsupported,jdk.crypto.ec,java.net.http,jdk.localedata,java.instrument \
    --include-locales en,it,es,nl,fr,de,ro,pt,tr,pl,da,bg,sv,cs \
    --output /jlinked

RUN /jlinked/bin/java -Xshare:dump -XX:+UseSerialGC -version
RUN "$JAVA_HOME/bin/java" -Djarmode=tools -jar alfio-boot.jar extract --destination /cds && \
    (cd /cds && java -XX:ArchiveClassesAtExit=alfio-cds.jsa -Dspring.profiles.active=app-cds -Dspring.context.exit=onRefresh -jar alfio-boot.jar)


FROM alpine:3.19
LABEL org.opencontainers.image.source=https://github.com/alfio-event/alf.io

COPY --from=zulu /jlinked /opt/jdk/

ENV LANG en_US.UTF-8
RUN addgroup -S alfio  \
    && adduser -h /home/alfio -u 1001 -G alfio -S alfio  \
    && apk update  \
    && apk add --update ttf-dejavu  \
    && rm -rf /var/cache/apk/*

USER 1001

# Define working directory.
RUN mkdir /home/alfio/app
WORKDIR /home/alfio/app

COPY --chown=alfio --from=zulu /cds/* /home/alfio/app/

ENV ALFIO_JAVA_OPTS=""
ENV ALFIO_PERFORMANCE_OPTS="-Dspring.jmx.enabled=false -Dlog4j2.disableJmx=true"

CMD /opt/jdk/bin/java $ALFIO_JAVA_OPTS $ALFIO_PERFORMANCE_OPTS -XX:+UseContainerSupport \
    -XX:SharedArchiveFile=alfio-cds.jsa -jar alfio-boot.jar

EXPOSE 8080
