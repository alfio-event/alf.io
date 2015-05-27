alf.io
========

alf.io

[![Build Status](http://img.shields.io/travis/exteso/alf.io/master.svg)](https://travis-ci.org/exteso/alf.io) [![Coverage Status](https://img.shields.io/coveralls/exteso/alf.io.svg)](https://coveralls.io/r/exteso/alf.io)
[![Coverity Scan Build Status](https://img.shields.io/coverity/scan/5232.svg)](https://scan.coverity.com/projects/5232)

## Prerequisites

You should have installed Java version 8 (either [Oracle's](http://www.oracle.com/technetwork/java/javase/downloads/index.html) or [OpenJDK](http://openjdk.java.net/install/)) in order to build and run alf.io. Please note that for the build process the JDK is required.

## Run on your machine

## Gradle Build

This build includes a copy of the Gradle wrapper. You don't have to have Gradle installed on your system in order to build
the project. Simply execute the wrapper along with the appropriate task, for example

>./gradlew clean

### Running with multiple profiles

You must specify a project property at the command line, such as

>./gradlew -Pprofile=dev :bootRun

The following profiles are supported

 * dev
 * dev-pgsql
 * docker-test

You can get a list of all supported Gradle tasks by running

>./gradlew tasks --all

You can configure additional System properties (if you need them) by creating the following file and putting into it one property per line:
> vi custom.jvmargs

please be aware that since this file could contain sensitive information (such as Google Maps private API key) it will be automatically ignored by git
