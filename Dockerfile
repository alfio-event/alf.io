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

FROM alpine:3.8

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
COPY --chown=alfio src/main/webapp/WEB-INF    WEB-INF
COPY --chown=alfio src/main/webapp/resources  resources

ENV ALFIO_LOG_STDOUT_ONLY=true
ENV ALFIO_JAVA_OPTS=""
ENV ALFIO_PERFORMANCE_OPTS="-Dspring.jmx.enabled=false -Dlog4j2.disableJmx=true"

CMD /opt/jdk/bin/java $ALFIO_JAVA_OPTS $ALFIO_PERFORMANCE_OPTS -XX:+UseContainerSupport \
    --add-modules jdk.scripting.nashorn \
    -cp ./WEB-INF/classes:./resources:./WEB-INF/lib/*:./WEB-INF/lib-provided/* alfio.config.SpringBootLauncher

EXPOSE 8080
