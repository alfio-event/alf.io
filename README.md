alf.io
========

alf.io

[![Build Status](http://img.shields.io/travis/alfio-event/alf.io/master.svg)](https://travis-ci.org/alfio-event/alf.io) [![Coverage Status](https://img.shields.io/coveralls/alfio-event/alf.io.svg)](https://coveralls.io/r/alfio-event/alf.io)

## Warning

As the work for Alf.io [v2](https://github.com/alfio-event/alf.io/milestones) has started, this branch may contain **unstable** and **untested** code. 
If you want to build and deploy alf.io by yourself, we strongly suggest you to use the [1.x-maintenance](https://github.com/alfio-event/alf.io/tree/1.x-maintenance) branch.  

## Prerequisites

You should have installed Java version 8 (either [Oracle's](http://www.oracle.com/technetwork/java/javase/downloads/index.html) or [OpenJDK](http://openjdk.java.net/install/)) in order to build and run alf.io. Please note that for the build process the JDK is required.

## Run on your machine

### Gradle Build

This build includes a copy of the Gradle wrapper. You don't have to have Gradle installed on your system in order to build
the project. Simply execute the wrapper along with the appropriate task, for example

```
./gradlew clean
```

#### Running with multiple profiles

You must specify a project property at the command line, such as
```
./gradlew -Pprofile=dev :bootRun
```
The local "bootRun" task has the following prerequisites:

- a PostgreSQL instance up and runnning on localhost:5432
- a _postgres_ user having password: _password_
- a database named _alfio_

once started, alf.io will create all the required tables on the database.

Note: if you want to test without installing a pgsql instance, we have configured the following tasks:

- startEmbeddedPgSQL
- stopEmbeddedPgSQL

So, in a terminal first launch pgsql:

```
./gradlew startEmbeddedPgSQL
```

In another one launch alf.io

```
./gradlew -Pprofile=dev :bootRun
```

When you are done, kill the pgsql instance with:

```
./gradlew stopEmbeddedPgSQL
```


The following profiles are supported

 * `dev`
 * `dev-pgsql`
 * `docker-test`

You can get a list of all supported Gradle tasks by running
```
./gradlew tasks --all
```

You can configure additional System properties (if you need them) by creating the following file and putting into it one property per line:
```
vi custom.jvmargs
```

Please be aware that since this file could contain sensitive information (such as Google Maps private API key) it will be automatically ignored by git.

#### For debug

Add a new line with: `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005` in custom.jvmargs


## Developing alf.io
Importing the Gradle project into Intellij and Eclipse both work.

**Note**: this project uses [Project Lombok](https://projectlombok.org/). You will need to install the corresponding Lombok plugin for integration into your IDE.

## Check dependencies to update

`./gradlew dependencyUpdates`

## Docker

alf.io can be run for development with Docker Compose:

    docker-compose up

If you plan on using Docker Compose to run alf.io in production, then you need
to make a couple of changes:

* Add a mapping for port `8443`
* Handle SSL termination (e.g. with something like `tutum/haproxy`)
* Remove the `SPRING_PROFILES_ACTIVE: dev` environment variable

### Test alf.io application
 * Check alfio logs: `docker logs alfio`
 * Copy admin password in a secure place
 * Get IP of your docker container: (only on Mac/Windows, on linux the proxy will bind directly on your public IP)
    * `boot2docker ip` on Mac/Windows
 * Open browser at: `https://DOCKER_IP/admin`
 * Insert user admin and the password you just copied

### Generate a new version of the alfio/alf.io docker image

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
 docker build -t alfio/alf.io .
 ```

### About the included AppleWWDRCA.cer

The certificate at src/main/resources/alfio/certificates/AppleWWDRCA.cer has been imported for https://github.com/ryantenney/passkit4j#usage functionality.
It will expire the 02/07/23 (as https://www.apple.com/certificateauthority/).

## Available spring profiles:

 - dev: enable dev mode
 - spring-boot: added when launched by spring-boot
 - demo: enable demo mode, the accounts for the admin will be created on the fly
 - disable-jobs: disable jobs
