#
# Alf.io dockerfile.
#

# Pull base image.
FROM adoptopenjdk/openjdk8-openj9:jdk8u162-b12_openj9-0.8.0

RUN useradd -ms /bin/bash -u 1001 alfio
RUN apt-get update \
 && apt-get install --assume-yes apt-utils \
 && apt-get install --assume-yes software-properties-common \
 && apt-get install --assume-yes dconf-cli \
 && apt-get install --assume-yes fontconfig

USER alfio
COPY ./alfio-@ALFIO_VERSION@-boot.war /home/alfio/alfio-boot.war

# Define working directory.
WORKDIR /home/alfio
RUN mkdir /home/alfio/logs
ENV ALFIO_LOG_STDOUT_ONLY=true
ENV ALFIO_JAVA_OPTS=""

CMD java $ALFIO_JAVA_OPTS -jar /home/alfio/alfio-boot.war

EXPOSE 8080
