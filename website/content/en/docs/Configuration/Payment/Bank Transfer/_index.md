---
title: "Bank Transfer (offline)"
linkTitle: "Bank Transfer"
date: 2017-01-05
weight: 1
description: >
  How to configure payment via Bank Transfer.
---

If you want to accept payments via Bank Transfer, head to the Payment section of your Organization's settings:

![Offline Payment Configuration](/img/configuration/payment/offline/001.png)

### Bank Transfer enabled

Enable the Bank Transfer payment method at organization level

### Cancel Reservation automatically when payment is overdue

- **default: true** 2.0-M1
- **default: false** 2.0-M2+

Controls whether or not to delete automatically reservations whose payment is expired

### Maximum number of days allowed to pay an offline ticket

**default: 5**

The number of days available to the customer to pay

### How many hours before expiration should be sent a reminder e-mail for offline payments

**default: 24**

This parameter tells alf.io when it should send a reminder/warning email to the customer

### Bank account number

**required** The Bank account number (IBAN) where the customer must transfer the money

### Bank account owner

Details about the organizer's Bank Account. E.g. Address of the bank and SWIFT code for international payments


## How it works

### Reservation Process

#### Payment Method selection

If you enable Bank Transfer, the customer will have the option of selecting the "Bank Transfer" payment method

![Bank Transfer Payment Method](/img/configuration/payment/offline/002.png)

#### Confirmation Page

the next step, after confirmation, is the "Payment required" page:

![Payment required](/img/configuration/payment/offline/003.png)

### Backoffice (confirmation)

#### Access Pending Payment Reservations

To handle Reservations with a pending payment, head to the Event Detail page, and click on the "Pending Payments" menu:

![Pending Payment](/img/configuration/payment/offline/004.png)

#### Confirm or delete Payments

Once you're on the Pending Payments page, you can confirm or delete payments

![Pending Payment](/img/configuration/payment/offline/005.png)

You can also perform a bulk confirmation by uploading a CSV file with the received payments.

