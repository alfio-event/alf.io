alf.io
========

alf.io

[![Build Status](http://img.shields.io/travis/alfio-event/alf.io/master.svg)](https://travis-ci.org/alfio-event/alf.io) [![Coverage Status](https://img.shields.io/coveralls/alfio-event/alf.io.svg)](https://coveralls.io/r/alfio-event/alf.io)

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

Please be aware that since this file could contain sensitive information (such as Google Maps private API key) it will be automatically ignored by git.

#### For debug

Add a new line with: `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005` in custom.jvmargs


#### Using hsqldb gui

In the custom.jvmargs add the following 2 lines:

```
-Djava.awt.headless=false
-DstartDBManager=true
```

Then, when executing `./gradlew -Pprofile=dev :bootRun`, the ui will automatically launch.


## Developing alf.io
Importing the Gradle project into Intellij and Eclipse both work.

**Note**: this project uses [Project Lombok](https://projectlombok.org/). You will need to install the corresponding Lombok plugin for integration into your IDE.

## Check dependencies to update

`./gradlew dependencyUpdates`


## Run on OpenShift

You'll either need to [locally install minishift](https://docs.openshift.org/latest/minishift/index.html)
(great for testing! perhaps slightly increase resources from the default via `minishift config set memory 4096; minishift config set cpus 4`),
or need to create at least a FREE TRIAL ;-) account on [openshift.com](https://www.openshift.com).
Either way you'll use the OC CLI, like this:

    oc login https://... --token=...

    oc new-project alf-io

    oc apply -f openshift.yaml

    oc start-build alfio

    oc logs -f bc/alfio

    oc expose svc/alfio

    oc status

NB: This does NOT use the Docker images (below) from DockerHub, but instead builds a container from source using [OpenShift's S2I Java Builder feature](https://github.com/fabric8io-images/s2i/tree/master/java/examples), thanks to this project's configuration in [.s2i/](.s2i/), and uses [OpenShift's PostgreSQL](https://docs.okd.io/latest/using_images/db_images/postgresql.html) ([from here](https://github.com/sclorg/postgresql-container)).

To use TLS on a Route with a custom Hostname, [use openshift-acme](https://github.com/tnozicka/openshift-acme/tree/master/deploy/letsencrypt-live/single-namespace) which will automagically add a Cert from [Let's Encrypt](https://letsencrypt.org) as soon as you add the `metadata: annotations: kubernetes.io/tls-acme: "true"` to your Route.


## Docker images

### Pull the latest stable version from Docker Hub

 ```
 docker pull alfio/alf.io
 ```

Here's an example of deployment as a 3 tier application using the following images:

 * `postgres` --> docker official image for PostgreSQL database
 * `alfio/alf.io`--> application runtime.
 * `tutum/haproxy` --> front layer proxy, force redirect to https and support load-balancing if multiple alf.io instances are running


### Launch alf.io container instances

 * Define local directory for database data (on docker host, for data to survive postgres image restarts):  `/path/to/local/data = /data/postgres/alfio`

 * Launch the Postgres instance

 ```
 docker run --name alfio-db -e POSTGRES_DB=postgres -e POSTGRES_USERNAME=postgres -e POSTGRES_PASSWORD=alfiopassword --restart=always -d -v /path/to/local/data:/var/lib/postgresql/data postgres
 ```
    * Note: on Mac volumes don't work (see https://jhipster.github.io/installation.html for a possible workaround), launch the above command without the `-v` parameter (data are lost at every restart)

 * Launch the alf.io server
 ```
 docker run --name alfio --link alfio-db:postgres -d alfio/alf.io
 ```
    Please note that at the moment, the only alias supported for the DB link is *postgres*

 * Launch the proxy
 ```
 docker run --name alfio-proxy --link alfio-web:web1 -e SSL_CERT="$(awk 1 ORS='\\n' src/main/dist/servercert.pem)" -e FORCE_SSL=yes -e PORT=8080 -p 443:443 -p 80:80 -d tutum/haproxy
 ```

### Test alf.io application
 * Check alfio-web logs: `docker logs alfio-web`
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
 - debug-csp: add report-uri and log csp violations
 - http: enable if behind proxy or the call chain is not full https
 - spring-boot: added when launched by spring-boot
 - demo: enable demo mode, the accounts for the admin will be created on the fly
 - disable-jobs: disable jobs
 - jdbc-session: enable saving the http session in the database
