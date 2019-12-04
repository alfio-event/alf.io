---
title: "Label Content"
linkTitle: "Label Content"
weight: 3
description: >
  Customize label content
---

With Alf.io you can configure what to print on your attendees' badges.

## Information that can be printed

The following information are supported:

- Basic Information (First Name, Last Name)
- Additional Information, as defined in the "Attendees' Data to collect" section
- Static Text (i.e. "Welcome Kit")
- Checkbox on the last printed row

## How to Configure label printing

From the Event Detail page, select _Actions_ -> _Edit configuration_

![edit configuration](/img/event-management/check-in/configure-badge/001.png)

Then head to the Check-in settings section, and:

- select the option _Label Printing Enabled_
- edit the Label layout using the textarea on the left

![badge configuration](/img/event-management/check-in/configure-badge/002.png)

This option requires you to provide a valid [JSON](https://en.wikipedia.org/wiki/JSON) configuration file.
Here's an example
```json
{
  "qrCode": {
    "additionalInfo": [],
    "infoSeparator": "::"
  },
  "content": {
    "firstRow": "firstName",
    "secondRow": "lastName",
    "additionalRows": ["company", "jobTitle", "static-text:Welcome Bag"],
    "checkbox": true
  },
  "general": {
    "printPartialID": true
  }
}
```

### QR Code element

per default, QR Code contains the ID of the ticket. This is for allowing authorized Sponsors to scan it and collect contacts of the attendees showing up at their booth.

However, if you have already an existing scanning system for sponsors, you can configure the QR Code content in order to be compatible with the scanning system.

{{% pageinfo %}}
Please note that the first element in the QR Code will always be the ticket ID. This is done to preserve compatibility with the "Alf.io Scan" app.
{{% /pageinfo %}}

an example could be:

```json
  "qrCode": {
    "additionalInfo": ["firstName","lastName","company"],
    "infoSeparator": "::"
  }
```

this would generate a QR Code with the following text:

`ID-OF-TICKET::Homer::Simpson::Springfield Nuclear Plant`

### Label Text Content

```json
"content": {
  "firstRow": "firstName",
  "secondRow": "lastName",
  "additionalRows": ["company", "jobTitle", "static-text:Welcome Bag"],
  "checkbox": true
}
```
The above configuration specify a layout with five rows:

- First Name
- Last Name
- Company name
- Job Title
- the text _Welcome Bag_

Additionally, a checkbox will be printed at the beginning of the fifth row, to allow the team to mark the badge if someone collects their welcome bag

### How reference to additional information works

As you've seen in the badge configuration, we include to the following additional information:

- company
- jobTitle

We can do that because our event is configured to collect the following information from all attendees:

![additional info](/img/event-management/check-in/configure-badge/003.png)

#### The special case of Company Name

If you have enabled [invoicing](/docs/configuration/invoice/) for your event, and the `company` keyword is present in the JSON, alf.io will print:

- the value of the field `company` if present for the scanned ticket and not empty, or 
- the Company Name, as written on the invoice, if present, or
- an empty row

### The "General" section

```json
"general": {
  "printPartialID": true
}
```

the only available option here is: `printPartialID`. It instructs alf.io whether or not to print a short ID of the ticket below the QR Code.