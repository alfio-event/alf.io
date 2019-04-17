#
# Alf.io dockerfile.
# Thanks to:
# - https://mjg123.github.io/2018/11/05/alpine-jdk11-images.html
# - https://august.nagro.us/small-java.html
#

FROM azul/zulu-openjdk-alpine:11 as zulu

RUN export ZULU_FOLDER=`ls /usr/lib/jvm/` \
    && jlink --compress=1 --strip-debug --no-header-files --no-man-pages \
    --module-path /usr/lib/jvm/$ZULU_FOLDER/jmods \
    # see https://github.com/ben-manes/caffeine/issues/273
    # see https://docs.oracle.com/en/java/javase/11/security/oracle-providers.html#GUID-9224B90B-7B2F-41F9-BB96-C0A1B6A0FEAA
    --add-modules jdk.scripting.nashorn,java.desktop,java.logging,java.sql,java.management,java.naming,jdk.unsupported,jdk.crypto.ec,java.net.http \
    --output /jlinked

FROM alpine:latest

COPY --from=zulu /jlinked /opt/jdk/

ENV LANG en_US.UTF-8
RUN addgroup -S alfio
RUN adduser -h /home/alfio -u 1001 -G alfio -S alfio
RUN apk update
RUN apk add --update ttf-dejavu && rm -rf /var/cache/apk/*

USER 1001

# Define working directory.
RUN mkdir /home/alfio/app
WORKDIR /home/alfio/app

RUN mkdir logs
COPY --chown=alfio WEB-INF WEB-INF
COPY --chown=alfio resources resources

ENV ALFIO_LOG_STDOUT_ONLY=true
ENV ALFIO_JAVA_OPTS=""
ENV ALFIO_PERFORMANCE_OPTS="-Dspring.jmx.enabled=false -Dlog4j2.disableJmx=true"

CMD /opt/jdk/bin/java $ALFIO_JAVA_OPTS $ALFIO_PERFORMANCE_OPTS -XX:+UseContainerSupport \
    --add-modules jdk.scripting.nashorn \
    -cp ./WEB-INF/classes:./resources:./WEB-INF/lib/*:./WEB-INF/lib-provided/* alfio.config.SpringBootLauncher

EXPOSE 8080
