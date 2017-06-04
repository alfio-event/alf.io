#
# @APP_NAME@ dockerfile.
#

# Pull base image.
FROM @BASE_IMAGE@

COPY ./@APP_NAME@-@APP_VERSION@-boot.war /alf.io/

# Define working directory.
WORKDIR /alf.io
RUN mkdir /alf.io/logs

VOLUME ["/alf.io/logs"]

ENTRYPOINT ["java", "-jar", "/alf.io/@APP_NAME@-@APP_VERSION@-boot.war"]

EXPOSE @PORT@
