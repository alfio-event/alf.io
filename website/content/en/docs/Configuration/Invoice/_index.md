---
title: "Invoice Generation"
linkTitle: "Invoice"
date: 2017-01-05
weight: 2
description: >
  How to configure alf.io to generate invoices.
---

## Enable the invoice subsystem

Head to the "Invoice" section of your organization's configuration

![Invoice configuration](/img/configuration/invoice/standard/001.png)

--------------------------------------------------------
### VAT number
Your Organization's VAT number - **Required**

### Invoice number Pattern

The pattern to apply when generating the invoice number. 
The default is to increment an organization-based counter.

#### Customize number

Here some examples of how you can customize the invoice number:

Description | Value  | Result
------------- | ------------- | -------------
prepend the "INVOICE-" text |  `INVOICE-%d` | `INVOICE-1`
append the "-INVOICE" text  | `%d-INVOICE`  | `1-INVOICE`
prepend the "INVOICE-" text, ensure a length of 3 zero-padded  |  `INVOICE-%03d` | `INVOICE-001`


### Invoice Address
Enter here the address of your Organization, as it should appear on the invoice

## Additional options

### Use invoice number for public references
Enable this flag if you want to use the invoice number as reference number, instead of the Reservation ID

### VAT/GST Number is required for Business Customers
Enable this flag if you want to force every business customer to enter their TAX ID number.

### Generate only Invoice
Enable this flag if you want to generate only invoices. The default is to let the attendee choose whether they want an invoice or a payment receipt.

### Enable the support for Italian e-invoicing
Enable this flag if your business is based in Italy and you must comply with the E-Invoicing regulation, more [here](./italian-e-invoice/)

## How it works

### Reservation Process

Depending on the value of the [Generate only Invoice](#generate-only-invoice) flag, the customer might have the possibility to choose whether or not they want to generate an invoice:

![I want an invoice](/img/configuration/invoice/standard/003.png)

Once they fill up the billing data and confirm, the Invoice Details will be saved

![Invoice Details](/img/configuration/invoice/standard/004.png)

they can check the invoice details and update if needed.

Once the reservation is complete:

- The invoice will be attached to the confirmation email
- The customer will be able to download the invoice from the confirmation page

### Backoffice

#### Invoice management for reservation

If you want to regenerate/download an invoice for a reservation, head to the Reservation Detail, and click on the "Billing Documents" tab

![Download Single Invoice](/img/configuration/invoice/standard/005.png)

#### Download all Billing Documents for an event

You can download all the invoices generated for an event, by selecting _Download_ -> _all Billing Documents_ on the event detail page

![Download All Billing Documents](/img/configuration/invoice/standard/006.png)