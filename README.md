alf.io
========

alf.io

[![Financial Contributors on Open Collective](https://opencollective.com/alfio/all/badge.svg?label=financial+contributors)](https://opencollective.com/alfio) [![Build Status](http://img.shields.io/travis/alfio-event/alf.io/master.svg)](https://travis-ci.org/alfio-event/alf.io) [![Coverage Status](https://img.shields.io/coveralls/alfio-event/alf.io.svg)](https://coveralls.io/r/alfio-event/alf.io)
[![Docker Hub Pulls](https://img.shields.io/docker/pulls/alfio/alf.io.svg)](https://hub.docker.com/r/alfio/alf.io/tags)
[![Open Source Helpers](https://www.codetriage.com/exteso/alf.io/badges/users.svg)](https://www.codetriage.com/exteso/alf.io)

## Warning

As the work for Alf.io [v2](https://github.com/alfio-event/alf.io/milestones) has started, this branch may contain **unstable** and **untested** code.
If you want to build and deploy alf.io by yourself, we strongly suggest you to use the [2.0-M1-maintenance](https://github.com/alfio-event/alf.io/tree/2.0-M1-maintenance) branch, as it contains production-ready code.

## Prerequisites

You should have installed Java version **11** (e.g. [Oracle's](http://www.oracle.com/technetwork/java/javase/downloads/index.html), [OpenJDK](http://openjdk.java.net/install/), or any other distribution) to build and run alf.io. Please note that for the build process the JDK is required.

Postgresql version 9.6 or later.

Additionally, the database user that creates and uses the tables should not be a "SUPERUSER", or else the row security policy checks will not be applied.

## Run on your machine

### Gradle Build

This build includes a copy of the Gradle wrapper. You don't have to have Gradle installed on your system to build
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

- a PostgreSQL (version 9.6 or later) instance up and running on localhost:5432
- a _postgres_ user having a password: _password_
- a database named _alfio_

once started, alf.io will create all the required tables in the database, and be available at http://localhost:8080/admin. You can log in using the default Username _admin_ and the password which was printed on the console.

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

Container images are available on https://hub.docker.com/r/alfio/alf.io/tags.

alf.io can also be run with Docker Compose:

    docker-compose up

If you plan on using Docker Compose to run alf.io in production, then you need
to make a couple of changes:

* Add a mapping for port `8443`
* Handle SSL termination (e.g. with something like `tutum/haproxy`)
* Remove the `SPRING_PROFILES_ACTIVE: dev` environment variable

### Test alf.io application
 * Check alfio logs: `docker logs alfio`
 * Copy admin password in a secure place
 * Get IP of your docker container: (only on Mac/Windows, on Linux the proxy will bind directly on your public IP)
    * `boot2docker IP` on Mac/Windows
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

## Contributors

### Code Contributors

This project exists thanks to all the people who contribute. [[Contribute](CONTRIBUTING.md)].
<a href="https://github.com/alfio-event/alf.io/graphs/contributors"><img src="https://opencollective.com/alfio/contributors.svg?width=890&button=false" /></a>

### Financial Contributors

Become a financial contributor and help us sustain our community. [[Contribute](https://opencollective.com/alfio/contribute)]

#### Individuals

<a href="https://opencollective.com/alfio"><img src="https://opencollective.com/alfio/individuals.svg?width=890"></a>

#### Organizations

Support this project with your organization. Your logo will show up here with a link to your website. [[Contribute](https://opencollective.com/alfio/contribute)]

<a href="https://opencollective.com/alfio/organization/0/website"><img src="https://opencollective.com/alfio/organization/0/avatar.svg"></a>
<a href="https://opencollective.com/alfio/organization/1/website"><img src="https://opencollective.com/alfio/organization/1/avatar.svg"></a>
<a href="https://opencollective.com/alfio/organization/2/website"><img src="https://opencollective.com/alfio/organization/2/avatar.svg"></a>
<a href="https://opencollective.com/alfio/organization/3/website"><img src="https://opencollective.com/alfio/organization/3/avatar.svg"></a>
<a href="https://opencollective.com/alfio/organization/4/website"><img src="https://opencollective.com/alfio/organization/4/avatar.svg"></a>
<a href="https://opencollective.com/alfio/organization/5/website"><img src="https://opencollective.com/alfio/organization/5/avatar.svg"></a>
<a href="https://opencollective.com/alfio/organization/6/website"><img src="https://opencollective.com/alfio/organization/6/avatar.svg"></a>
<a href="https://opencollective.com/alfio/organization/7/website"><img src="https://opencollective.com/alfio/organization/7/avatar.svg"></a>
<a href="https://opencollective.com/alfio/organization/8/website"><img src="https://opencollective.com/alfio/organization/8/avatar.svg"></a>
<a href="https://opencollective.com/alfio/organization/9/website"><img src="https://opencollective.com/alfio/organization/9/avatar.svg"></a>
