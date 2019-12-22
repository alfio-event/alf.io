---
title: "Italian E-Invoice"
linkTitle: "IT E-Invoice"
date: 2017-01-05
weight: 2
description: >
  How to configure alf.io to collect data for Italian E-Invoice.
---

## Context

If your business is based in Italy, you should check if you're expected to comply with the "Fatturazione Elettronica" (E-Invoicing) directive. More info [here](https://www.agenziaentrate.gov.it/portale/web/guest/aree-tematiche/fatturazione-elettronica/guida-fatturazione-elettronica).

If you must issue "electronic invoices" (that is, submit the invoice to the taxation authority **instead of** sending them directly to your customers), alf.io helps you to collect all the required information for doing that.

{{% alert title="Limitations" %}}
Alf.io is not capable of sending the invoices directly to the italian taxation authority, so you'll have to submit them manually.
{{% /alert %}}

## Configuration

### How to activate Italian E-Invoice

Please follow the tutorial on [how to activate invoices](../), then enable the following option:

![Enable the support for Italian e-Invoicing](/img/configuration/invoice/italian-e-invoice/001.png)

## How does it work

### Reservation Process

It is mandatory to register all the transactions, even if the customer is not a company.
Alf.io will request billing data for each customer buying a ticket, and if they set **Italy (IT)** as their billing country, they'll be asked to fill additional fields

![Additional fields for e-invoicing](/img/configuration/invoice/italian-e-invoice/002.png)

#### Fiscal Code (Codice Fiscale)

It's the Tax Code for private customers and companies. It is a required information

#### E-Invoice addressee (Destinatario Fattura Elettronica)

It specifies the final recipient of the invoice. It can be:

- **Addressee Code** (Codice Destinatario): an alphanumeric code assigned to companies or to invoicing systems.
- **PEC**: an certified e-mail address, see [more](https://en.wikipedia.org/wiki/Certified_email)
- **Neither**: if the buyer does not have none of the above

### Reservation Confirmed

Once the Reservation has been confirmed/paid by the customer, the organizer will receive a notification email with the following contents:

- The customer's billing details, as specified during the reservation process
- The Invoice in PDF format. This contains all the information that must be sent to the taxation authority