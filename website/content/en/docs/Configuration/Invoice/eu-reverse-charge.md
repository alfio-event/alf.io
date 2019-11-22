---
title: "EU Reverse Charge"
linkTitle: "EU Reverse Charge"
date: 2017-01-05
weight: 1
description: >
  How to enable EU Reverse Charge support.
---

Alf.io supports the [Reverse Charge](https://www.avalara.com/vatlive/en/eu-vat-rules/eu-vat-returns/reverse-charge-on-eu-vat.html) mechanism for EU-based B2B invoices. 

Since there's no general rule about the reverse charge (i.e. each Member State can decide whether or not to apply it), 
**Please check with your accountant if you must apply it or not**

To activate it, head to the Invoice section of your configuration

![Reverse Charge configuration](/img/configuration/invoice/standard/002.png)

### Enable EU Reverse Charge

**default: false**

Enable this flag if you want to activate the Reverse Charge mechanism

### Validate VAT using EU VIES Webservice

**default: true**

Controls whether or not to call the VIES Webservice to validate a given EU VAT number

### Apply VAT to non-EU B2B customers

**default: true**

Controls whether or not to apply VAT to non-EU business customers. This regulation is country-specific, please check with your accountant.

### The country where the organizer run its business

Select the Country where your business is based. This will be compared with the customer Country to determine if Reverse Charge must be applied.