---
title: "Pivotal Cloud Foundry"
linkTitle: "Pivotal CF"
weight: 2
description: >
  How to deploy alf.io on Pivotal(tm) Cloud Foundry
---

## Account setup

In order to proceed, you must create and activate an account on [Pivotal Cloud Foundry](https://run.pivotal.io).

After that you must download and install the [Pivotal cli tools](http://docs.run.pivotal.io/starting/)

## Push (deploy) the application

First of all run the following command to log yourself in:


```
$ cf login -a https://api.run.pivotal.io
```

enter your e-mail and password when requested. After that, you're ready to deploy your new application:


```
$ cf push *YOUR_APP_NAME* -p /path/to/alf.io-{VERSION}-boot.war --no-start
```



Please note that the application name will be part of the url (e.g. YOUR_APP_NAME.cfapps.io). That's it!



## Database configuration

Since the database is not yet configured, we cannot start the application. But don't worry, we're about to fix that.



### Create new database instance

Open the Dashboard, and click on *"Add Service"*

![add service to your application](/img/deployment/cloudfoundry/001.png)



### Select the database from marketplace

select *"ElephantSQL"*

![select ElephantSQL service](/img/deployment/cloudfoundry/002.png)


### Select the plan

ElephantSQL offers multiple plans. Just pick up the one that would fit your needs.

 
If you want to do a test-drive, you can easily select their free plan, 
otherwise we strongly suggest you to pick up one of their paid plans 
(*Simple Spider* should be fine)

![select ElephantSQL plan](/img/deployment/cloudfoundry/003.png)


### Connect the database to the Application

give a name to your database and bind it to your application

![bind database to application](/img/deployment/cloudfoundry/004.png)



## (Re)start your application

After adding the database, either (re)start the application from the console or run the following command:


```
$ cf restart YOUR_APP_NAME
```

![restart application from the console](/img/deployment/cloudfoundry/005.png)


## Retrieve admin password

check the logs

```
$ cf logs YOUR_APP_NAME --recent
```

and find the auto-generated password. Then you can access the admin under `https://YOUR_APP_NAME.cfapps.io/admin/`

![find password on log](/img/deployment/cloudfoundry/006.png)