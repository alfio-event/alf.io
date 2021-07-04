---
title: "Import existing attendees"
linkTitle: "Import existing attendees"
weight: 5
description: >
  How to import a list of existing attendees into Alf.io
---

## Create your event

Create your event on your alf.io instance. 

Define ticket categories, available seats, and additional information you need to collect.


### Define additional information

In this tutorial we'll use the following information: `company`, `gender`

![define additional information](/img/tutorials/import-attendees/res.1.png)


## Import Attendees

From your event's detail page, select `Actions`-> `Import Attendees`

![access the Import Attendees functionality](/img/tutorials/import-attendees/res.2.png)


### Fill in the contact info

Fill in the contact info for the reservation. 

You can decide later if you want a single reservation for all the attendees (max. 100) or if you want to create a reservation for each attendee.

![fill in the contact info](/img/tutorials/import-attendees/res.3.png)



### Create and upload attendees' file

In order to process your attendees and create a ticket for each of them.

**required information:**

- **firstName**
- **lastName**
- **email**
- **lang** - attendee's language. ISO-639 code (e.g. "en" for English, "de" for German...)


**information not required, but strongly recommended**

- **reference** the unique ticket-id from the existing system. This would prevent multiple imports of the same attendee


**additional information**, event-specific

- **company**
- **gender** (M, F, X)


#### Example CSV file

you can download an example CSV [here](/files/import-attendees/test-alfio.csv)

![upload CSV file](/img/tutorials/import-attendees/res.4.png)


### Target Ticket Category and options

You can import attendees for one ticket category. If you have multiple categories to import, please repeat the procedure multiple times

Additionally, the following options are available:

- **Create one reservation per attendee**: alf.io will create a Ticket Reservation for each attendee, as they were buying the ticket by themselves. **_Mandatory for > 100 attendees_**
- **Forbid ticket reassignment**: Alf.io allows tickets to be assigned to another person. If you don't want that, you can forbid it here
- **Send tickets via email**: If selected, Alf.io will send the ticket to the imported attendees right away
- **Add extra seats to event if needed**: if the event on alf.io is already sold-out, you can add extra seats for these attendees.

Once you upload the file, you'll see also the attendees' list here

![preview of the attendees](/img/tutorials/import-attendees/res.5.png)


### Confirm import

### Wait for the process to complete

The import process has been designed to be as less resource-consuming as possible, so that you can launch it while you're selling tickets.
On an average server it takes ~2 min to load 4000 attendees. Just be patient :-)

![progress](/img/tutorials/import-attendees/res.7.png)



## View Results

### Statistics

The added attendees will be included in the statistics. If you've selected a Ticket Category with price > 0 (in the example 80 CHF), the gross income will be updated accordingly

![statistics](/img/tutorials/import-attendees/res.8.png)



### View Tickets

You can find the added tickets by clicking on the "tickets" button in the corresponding Ticket Category

![tickets](/img/tutorials/import-attendees/res.9.png)