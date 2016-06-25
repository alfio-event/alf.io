alf.io
========

alf.io

[![Build Status](http://img.shields.io/travis/exteso/alf.io/master.svg)](https://travis-ci.org/exteso/alf.io) [![Coverage Status](https://img.shields.io/coveralls/exteso/alf.io.svg)](https://coveralls.io/r/exteso/alf.io)
[![Coverity Scan Build Status](https://img.shields.io/coverity/scan/5232.svg)](https://scan.coverity.com/projects/5232)

## Prerequisites

You should have installed Java version 8 (either [Oracle's](http://www.oracle.com/technetwork/java/javase/downloads/index.html) or [OpenJDK](http://openjdk.java.net/install/)) in order to build and run alf.io. Please note that for the build process the JDK is required.

## Run on your machine

### Gradle Build

This build includes a copy of the Gradle wrapper. You don't have to have Gradle installed on your system in order to build
the project. Simply execute the wrapper along with the appropriate task, for example

```
./gradlew clean
```

#### Running with multiple profiles

You must specify a project property at the command line, such as
```
./gradlew -Pprofile=dev :bootRun
```

The following profiles are supported

 * `dev`
 * `dev-pgsql`
 * `dev-mysql`
 * `docker-test`

You can get a list of all supported Gradle tasks by running
```
./gradlew tasks --all
```

You can configure additional System properties (if you need them) by creating the following file and putting into it one property per line:
```
vi custom.jvmargs
```

Please be aware that since this file could contain sensitive information (such as Google Maps private API key) it will be automatically ignored by git

## Developing alf.io
Importing the Gradle project into Intellij and Eclipse both work.

**Note**: this project uses [Project Lombok](https://projectlombok.org/). You will need to install the corresponding Lombok plugin for integration into your IDE.

**Note:** Intellij has trouble recognizing the Spring Bean context in this project and shows "errors" in the `@Autowired` annotation on constructors. These do not block compilation and can be ignored. If you find a way to eliminate the warnings, please [let us know](https://groups.google.com/d/msg/alfio/F6BPMOwsX48/Idd6X6z4BAAJ)!
## Docker images
Alf.io is also offered as a 3 tier application using 3 docker images:

 * `postgres` --> docker official image for PostgreSQL database
 * `exteso/alfio-web`--> application runtime. Docker image is generated from this project (see below).
 * `tutum/haproxy` --> front layer proxy, force redirect to https and support load-balancing if multiple alfio-web instances are running

### Generate a new version of the exteso/alfio-web docker image
 * Build application and Dockerfile:
 ```
 ./gradlew distribution
 ```

 * Enter the directory:
 ```
 cd build/dockerize
 ```

 * Create docker image:
 ```
 docker build -t exteso/alfio-web .
 ```

### Publish a new version of the exteso/alfio-web on docker hub
TODO

### Launch alf.io container instances
 * Define local directory for database data (on docker host, for data to survive postgres image restarts):  `/path/to/local/data = /data/postgres/alfio`

 * Define a local directory for logs: `/path/to/logs = /home/alfio/logs`

 * Launch the Postgres instance
 ```
 docker run --name alfio-db -e POSTGRES_DB=postgres -e POSTGRES_USERNAME=postgres -e POSTGRES_PASSWORD=alfiopassword --restart=always -d -v /path/to/local/data:/var/lib/postgresql/data postgres
 ```
    * Note: on Mac volumes don't work (see https://jhipster.github.io/installation.html for a possible workaround), launch the above command without the `-v` parameter (data are lost at every restart)

 * Launch the alf.io server
 ```
 docker run --name alfio-web --link alfio-db:db -v /path/to/logs:/alfio/logs -d exteso/alfio-web
 ```

 * Launch the proxy
 ```
 docker run --name alfio-proxy --link alfio-web:web1 -e SSL_CERT="$(awk 1 ORS='\\n' src/main/dist/servercert.pem)" -e FORCE_SSL=yes -e PORT=8080 -p 443:443 -p 80:80 -d tutum/haproxy
 ```

### Test alf.io application
 * See alfio-web logs: `docker logs alfio-web` or `less /path/to/logs/alfio.log`
 * Copy admin password in a secure place
 * Get IP of your docker container: (only on Mac/Windows, on linux the proxy will bind directly on your public IP)
    * `boot2docker ip` on Mac/Windows
 * Open browser at: `https://DOCKER_IP/admin`
 * Insert user admin and the password you just copied
