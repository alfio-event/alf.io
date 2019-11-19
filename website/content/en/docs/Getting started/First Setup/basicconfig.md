---
title: "Required options"
linkTitle: "Required Options"
weight: 3
date: 2019-10-14
description: >
  How to set the minimum, required information
---

This is the basic configuration screen you'll see when you log in 
as admin for the first time.

![](/img/getting-started/basic-config/basic-configuration-section.png)

#### General

The first option is to choose from the supported languages.
Then you need to enter the base application URL, which is the public URL of your instance.

#### E-Mail

please refer to the [relevant section](../../e-mail/) of this documentation.

#### Map

Next is the map configuration. This is to show the location of events.
The default here is none.
Alternatively, you can use either Google maps or Here maps.

##### Google Maps

![](/img/getting-started/basic-config/google-maps.PNG)

Google maps requires a client API key. For more detail on how to get an API key, click [here](https://developers.google.com/maps/documentation/javascript/get-api-key).

**Required APIs**

Google Maps integration requires you to enable the following APIs:

- [JavaScript API](https://console.developers.google.com/apis/api/maps-backend.googleapis.com/overview)
- [Static Maps API](https://console.developers.google.com/apis/api/static-maps-backend.googleapis.com/overview)

##### HERE Maps

![](/img/getting-started/basic-config/here-maps.PNG)

HERE is an alternative map provider. For more detail on how to use HERE
click [here](https://developer.here.com/).

**Required APIs**

Please make sure that you have the following APIs enabled/available:

- Geocoding API
- Static Maps API

------------------------------------------
### Credits

this page has been created by [ArthurDuckham](https://github.com/ArthurDuckham)
