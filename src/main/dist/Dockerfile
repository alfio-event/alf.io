#
# Alf.io dockerfile.
# Thanks to https://mjg123.github.io/2018/11/05/alpine-jdk11-images.html
#

FROM azul/zulu-openjdk-alpine:11 as zulu

RUN export ZULU_FOLDER=`ls /usr/lib/jvm/` \
    && jlink --compress=2 \
    --module-path /usr/lib/jvm/$ZULU_FOLDER/jmods \
    #see https://github.com/ben-manes/caffeine/issues/273
    --add-modules jdk.scripting.nashorn,java.desktop,java.logging,java.sql,java.management,java.naming,jdk.unsupported \
    --output /jlinked

FROM alpine:latest

COPY --from=zulu /jlinked /opt/jdk/

ENV LANG en_US.UTF-8
RUN adduser -h /home/alfio -u 1001 -S alfio
RUN apk add --update ttf-dejavu && rm -rf /var/cache/apk/*

USER 1001

RUN mkdir /home/alfio/app
WORKDIR /home/alfio/app

ADD WEB-INF/lib lib
ADD WEB-INF/lib-provided lib-provided
ADD WEB-INF/classes classes
ADD resources resources

# Define working directory.

RUN mkdir /home/alfio/logs
ENV ALFIO_LOG_STDOUT_ONLY=true
ENV ALFIO_JAVA_OPTS=""

CMD /opt/jdk/bin/java $ALFIO_JAVA_OPTS -Dspring.jmx.enabled=false -Dlog4j2.disableJmx=true -XX:+UseContainerSupport \
    --add-modules jdk.scripting.nashorn \
    -cp classes:resources:lib/*:lib-provided/* alfio.config.SpringBootLauncher

EXPOSE 8080
