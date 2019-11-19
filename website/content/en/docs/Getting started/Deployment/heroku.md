---
title: "Heroku"
linkTitle: "Heroku"
weight: 1
description: >
  How to deploy alf.io on Heroku
---

> Some of the following steps require that you have a [Java](https://www.oracle.com/technetwork/java/javase/downloads/index.html)(TM) Runtime Environment installed on your computer.

## Account setup

In order to proceed, you must create an account on [Heroku](https://www.heroku.com). After that you must download and install the [Heroku Toolbelt](https://toolbelt.heroku.com/)

## Create the application

Open a terminal and run the following command to log yourself in:

`heroku login`

enter your e-mail and password when requested. After that, you're ready to create your new application:

`heroku create YOUR_APP_NAME`

Please note that the application name will be part of the url (e.g. YOUR_APP_NAME.herokuapp.com)

## Edit Application properties

Open the Dashboard and select your application

![select your application](/img/deployment/heroku/001.png)


## Add new add-on

Open the "resources" tab, and click on the "+" near *"Find more add-ons..."*

![add new add-on](/img/deployment/heroku/002.png)

### Add Database instance

Search "Heroku Postgres" using the text field and then select the Heroku Postgres plan which fit your needs. 

For a test-drive we suggest to go with the "Hobby Dev" plan, otherwise "Hobby Basic" should be fine.


![Set up Postgres on Heroku](/img/deployment/heroku/003.png)

## Install Heroku Deploy plugin

In order to deploy the application you need to install the [Heroku-Deploy](https://github.com/heroku/heroku-cli-deploy) toolbelt plugin:

`heroku plugins:install heroku-cli-deploy`

## Deploy the application

create a new directory and move into it

`mkdir alfio-heroku; cd alfio-heroku`

download `alfio-{VERSION}-boot.war` from [github](https://github.com/exteso/alf.io/releases/latest)

`curl -OL https://github.com/alfio-event/alf.io/releases/download/{VERSION}/alfio-{VERSION}-boot.war`

download the `Procfile` deployment descriptor:

`curl -OL https://raw.githubusercontent.com/alfio-event/alf.io/master/etc/heroku/Procfile`

then you can deploy your new application:

`heroku deploy:jar --jar alfio-{VERSION}-boot.war --jdk=11 --app YOUR_APP_NAME`

## Retrieve admin password

check the log

`heroku logs --source app --app YOUR_APP_NAME --tail`

and find the auto-generated password. Then you can access the admin under `https://YOUR_APP_NAME.herokuapp.com/admin/`

![Admin password is displayed on the log](/img/deployment/heroku/004.png)
