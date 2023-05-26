---
title: "Google(tm) Wallet Integration"
linkTitle: "Google(tm) Wallet"
weight: 6
description: >
    How to generate Google(tm) passes for your tickets
---

## How to configure Google (tm) Wallet integration

Alf.io supports [Google(tm) Wallet](https://developers.google.com/wallet). Once the integration is active, attendees can save their tickets on Wallet, available on all their devices.

### Configure and activate your issuer account

follow steps **1 to 4** of the [Official prerequisites](https://developers.google.com/wallet/tickets/events/web/prerequisites);
Alf.io will take care of generating a wallet class for your event when needed.

### Configure alf.io to enable Wallet integration

Go to the system configuration, then find the "Mobile wallet integration" section and fill the required configuration:

![Configuration for Google Wallet](/img/configuration/google-wallet/001-settings.png)

#### Enable integration

This flags allows you to enable/disable the integration with Google (tm) Wallet

#### Overwrite previous EventClass and EventObject definitions

Activate this flag if you want to force update on the EventClass. For example, if you change properties that have an impact on the attendees' tickets, like event date, ticket validity and so on

#### Google Wallet issuer ID

The issuer ID that you see on the Google Wallet API Dashboard

#### Google Wallet Service Account Key in JSON format

The service account key alf.io will use to authenticate against the Wallet API

1. Follow the [Official guide](https://cloud.google.com/iam/docs/creating-managing-service-account-keys) to generate a new Key for the service account you've previously created.
2. Download the key file as json, open it using a text editor and copy its content
3. Paste the content in the alf.io admin


after this configuration, your attendees will be able to save their tickets into Google (tm) Wallet!