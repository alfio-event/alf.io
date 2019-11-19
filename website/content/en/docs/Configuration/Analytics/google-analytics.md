---
title: "Google Analytics"
linkTitle: "Google Analytics"
date: 2019-11-19
weight: 10
description: >
  Set Up Google Analytics Goals
---

## Set the correct tracking ID in alf.io

In Google analytics create a new property or go to an existing one and get the tracking ID. 

It has the following form: UA-XXXXXXXX-X (where X are numbers).

Go to the configuration section of your alf.io instance, insert the tracking id and save.

![update tracking ID](/img/tutorials/google-analytics/001.png)

## Configure the goal in google analytics

### Access the Google Analytics console

In the admin section of google analytics, after selecting the account and property, in the right there is a "Goals" link as depicted below. 
Click it.

![create goal](/img/tutorials/google-analytics/002.png)

### Create Goal

There is a list of goals and the possibility to create a new one. Click the "_+New Goal_" button:

![create goal](/img/tutorials/google-analytics/003.png)

### Get the ID of your event

The ID of your event is included in its URL. For example, if the event URL is https://example.com/event/my-event/, the ID will be "**my-event**".

Then in the first step, as pictured in the image below:

![configure goal](/img/tutorials/google-analytics/004.png)


Give a name to the goal and select the Type as "Destination". Click "Continue".

### Final configuration

![configure goal](/img/tutorials/google-analytics/005.png)

As a reminder, the event ID we are considering is "my-event", thus in all the paths, "my-event" will be present.

- The destination must be a "Regular expression" with `/event/my-event/reservation/[^/]+/success` as a value
- Funnel must be enabled (On)
- As a first step in the funnel, we set as a name "ticket selection page" and as a Page `/event/my-event/`
- As a second step, we set as a name "payment page" and as a Page `/event/my-event/[^/]+/book`

Click save and enjoy!
