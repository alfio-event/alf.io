---
title: "How-to set-up Mollie"
linkTitle: "Mollie"
date: 2022-05-20
weight: 2
description: >
  How-to set-up Mollie as payment provider
---
#### Support
Not all the payment methods supported by Mollie are supported by Alf.io

The following payment methods are supported by Alf.io:
- IDEAL
- Creditcard
- Apple Pay
- Bankcontact
- ING home pay
- Belfius
- Przelewy24
- KBC

## How-to set-up Mollie as payment provider
To start using Mollie as your payment provider in Alf.io you first have to obtain two things from your Mollie dashboard. Your profile id and your api key (test or live). 

### Obtaining the required info from your Mollie dashboard
To obtain the Profile ID and API-key, go to your [Mollie dashboard](https://my.mollie.com/dashboard/) In the left navigational-column of the dashboard click on Developers, then choose API-keys. Now select the appropriate website profile and copy the Profile-ID and API-key.
![developers section in Mollie dashboard](/website/static/img/configuration/Mollie/API-keys.png)
### Configuring Alf.io for Mollie
To set-up Alf.io for Mollie usage, go to Configuration and click on Payment. Then scroll to Mollie configuration.
1. Enable the configuration
2. If you have a Live-API key enable Live mode, if you have a test key disable Live mode.
3. Paste the API key
4. Paste your Profile ID in the box.

#### Dont forget to save the configurations by clicking on save on the bottom of the page. And test Mollie by making a reservation, before production use.
