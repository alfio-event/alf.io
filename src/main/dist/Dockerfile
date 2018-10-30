#
# Alf.io dockerfile.
#

# Pull base image.
FROM adoptopenjdk/openjdk11-openj9

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

CMD java $ALFIO_JAVA_OPTS -XX:+UseContainerSupport --add-modules jdk.scripting.nashorn -jar /home/alfio/alfio-boot.war

EXPOSE 8080
