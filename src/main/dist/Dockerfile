#
# Alf.io dockerfile.
#

# Pull base image.
FROM adoptopenjdk/openjdk8-openj9:slim

RUN useradd -ms /bin/bash alfio
USER alfio
COPY ./alfio-@ALFIO_VERSION@-boot.war /home/alfio/alfio-boot.war

# Define working directory.
WORKDIR /home/alfio
RUN mkdir /home/alfio/logs
ENV ALFIO_LOG_STDOUT_ONLY=true
ENV ALFIO_JAVA_OPTS=""

CMD java $ALFIO_JAVA_OPTS -jar /home/alfio/alfio-boot.war

EXPOSE 8080
