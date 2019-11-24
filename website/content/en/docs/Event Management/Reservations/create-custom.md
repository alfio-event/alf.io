---
title: "Create Reservation from Backoffice"
linkTitle: "Create from Backoffice"
weight: 1
description: >
  How to create Reservations from Backoffice
---

## Why and when?

Alf.io limits the number of tickets (default 5, configurable) that can be purchased in a single Reservation from the public. 

This is for minimizing tickets contention, especially on small events, because when a Reservation is initialized its tickets are locked until the Reservation expires.

But what happens if you need to register a group of more than 5 people? Let's simulate this with an example

### Sample use case

let's say that you're organizing "My Awesome Event", and you have:

- 185 seats available for attendees, 100 CHF each
- 10 seats for Speakers
- 5 for the organizing team

for a total of 200 people.

So you can create an event with the following categories:

Category | Visibility | Price | Max Seats
------------- | ------------- | ------------- | -------------
General Admission | Public | 100 | Dynamic
Speakers | Hidden | 0 | 10
Team | Hidden | 0 | 5

Then:

1. Ms. Jane Doe, member of _Example Org._, reaches out because she wants to attend your event along with 9 colleagues, but by company policy they must buy all the tickets  with a single transaction (you know, Purchase Orders)
1. You want to register the organizing team, so that you all have a pass for the event


## Example 1: create a pending Reservation

_Create Reservation_ is one of the most advanced and powerful tools in Alf.io. It gives full flexibility to the organizer as it's not subjected to the constraints imposed for the public. 

Here's how to initialize the Reservation for Ms. Jane Doe and her colleagues.

### Access the "Create Reservation" page

from the event detail page, click on _Actions_ -> _create Reservation_

![create Reservation menu](/img/event-management/custom-Reservation/001.png)

### Insert contact data

The contact person for this Reservation is Jane Doe, as she will take care of the payment. So let's insert her name in the relevant section

![insert contact data](/img/event-management/custom-Reservation/002.png)

### Fill Reservation details

Since you're very happy that _Example Org._ will send 10 people to your event, you decide to give them one ticket for free, that is, 10% discount.

#### Reserve 9 "General Admission" Tickets

In the "Reservation Details" section, fill up the form as shown in the picture:

![reserve 9 general admission tickets](/img/event-management/custom-Reservation/003.png)

**Insert Attendees**

It is possible to pre-fill First name, last name and email address of the attendees, either by entering them directly or by uploading a CSV file. In this example you don't know how their names in advance, so you can reserve **9 empty seats**.

**Assign attendees to a Ticket Category**

It is possible to reserve tickets from an existing category, or to create in-place a new one. In this case you can select the existing _General Admission_ category.

Now it's time to add the free ticket. Click on the _add Attendees for another Category_ button

#### Add 1 free Ticket

![add 1 free ticket to the Reservation](/img/event-management/custom-Reservation/004.png)

**Insert Attendees**

Same as before: you don't know the name in advance, so you reserve **1 empty seat**

**Assign attendees to a Ticket Category**

Since you don't have (yet) a "Group discount" category, this time you need to select _Create new_ and enter the name and price of the category. 

Please note that:

- the "Group discount" category will be created as "hidden"
- you can reuse the "Group discount" the next time you create a Reservation, if needed

### Save the Reservation

now you can click on _Save and continue_. 

If everything's OK, you should land on the Reservation Detail page, where you can copy the link to share with Ms. Doe.

![copy Reservation link](/img/event-management/custom-Reservation/005.png)

{{% pageinfo %}}
**Expiration date**

By default, custom Reservations are set to expire at the end of the following day. If you want to modify that, just select another deadline and click on _Update_

{{%/pageinfo%}}

#### The Reservation link

Once you share the Reservation Link with Ms. Doe, she will be able to complete the Reservation and pay

![add 1 free ticket to the Reservation](/img/event-management/custom-Reservation/006.png)

## Example 2: create and confirm a Reservation

### Insert contact data

This time you can set yourself as contact person

![insert contact data](/img/event-management/custom-Reservation/007.png)

### Fill Reservation details

On the Reservation details section, you can fill all the Team's data and select the existing _Team_ category

![insert team](/img/event-management/custom-Reservation/008.png)

### Save Reservation

now you can click on _Save and continue_. 

If everything's OK, you should land on the Reservation Detail page

![copy Reservation link](/img/event-management/custom-Reservation/009.png)

### Confirm Reservation

This time you can confirm the Reservation directly. To do so, click on _Mark as Completed_. The page will reload and the status will be _COMPLETE_

![Reservation complete](/img/event-management/custom-Reservation/010.png)

{{% pageinfo %}}
**GDPR & Privacy Policy**

If you create and confirm a Reservation from the Backoffice, you must send the Terms and Conditions and Privacy Policy to the attendees outside of Alf.io, as they won't be asked to accept them. 

Please make sure that they're aware of your privacy policy.

{{%/pageinfo%}}

### Send Tickets

when a Reservation is confirmed from the Backoffice, Alf.io won't send the tickets automatically. 

You have to send them manually by clicking on _Send tickets via email_

![send tickets button](/img/event-management/custom-Reservation/011.png)

once clicked on the _Send tickets via email_ button, you can choose which tickets you want to (re)send. In this case, select all

![select tickets to send](/img/event-management/custom-Reservation/012.png)