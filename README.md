alf.io
========

alf.io

[![Build Status](https://travis-ci.org/exteso/alf.io.png?branch=master)](https://travis-ci.org/exteso/alf.io)

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