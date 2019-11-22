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
Enable this flag if your business is based in Italy and you must comply with the E-Invoicing regulation