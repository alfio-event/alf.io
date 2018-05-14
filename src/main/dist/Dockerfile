#
# @APP_NAME@ dockerfile.
#

# Pull base image.
FROM @BASE_IMAGE@

RUN adduser -S alfio
USER alfio
COPY ./@APP_NAME@-@APP_VERSION@-boot.war /home/alfio/alfio-boot.war

# Define working directory.
WORKDIR /home/alfio
RUN mkdir /home/alfio/logs
ENV ALFIO_LOG_STDOUT_ONLY=true
ENV JAVA_OPTS="-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:+UseConcMarkSweepGC"

CMD java $JAVA_OPTS -jar /home/alfio/alfio-boot.war

EXPOSE @PORT@
