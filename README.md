alf.io
========

alf.io

[![Build Status](http://img.shields.io/travis/exteso/alf.io/master.svg)](https://travis-ci.org/exteso/alf.io) [![Coverage Status](https://img.shields.io/coveralls/exteso/alf.io.svg)](https://coveralls.io/r/exteso/alf.io)

read the [Requirements](https://github.com/exteso/alf.io/wiki/Requirements)

## Run with jetty


By default, will use hsqldb with dev profile:

>mvn jetty:run

Use with pgsql (dev) 

>mvn jetty:run -Pdev-pgsql


## Notes


When running with hsqldb, you can launch the integrated db manager with:

>mvn jetty:run -DstartDBManager

You will need to do a refresh (View -> Refresh Tree) to see the up to date schema.


## Gradle Build

This build includes a copy of the Gradle wrapper. You don't have to have Gradle installed on your system in order to build
the project. Simply execute the wrapper along with the appropiate task, for example

>./gradlew clean

### Running with multiple profiles

You must specify a project property at the commmand line, such as

>./gradlew -Pprofile=dev appRun

The following profiles are supported

 * dev
 * dev-pgsql
 * docker-test

You can get a list of all supported Gradle tasks by running

>./gradlew tasks --all

