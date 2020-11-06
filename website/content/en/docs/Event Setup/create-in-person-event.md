---
title: "How to set up an event"
linkTitle: "Create event"
weight: 1
description: >
  How to sell / distribute tickets for your event
---

## How to create a new event

Click on the "Create new event" button in the "Events" view.
You will be asked to enter the following information:

- Basic info
- Event description
- URL configuration
- Logo
- Seats and payment info
- Categories

All of those fields are mandatory, so be sure to have the required information at hand when creating your event. 
When the form prompts you with options to choose from, go with the default: once the event is created you'll be able to tweak its configuration.
Once all the information have been entered, click on the "Save" button at the end of the page in order to finalize the creation. 

### Copy from previous event

You can also initialize the new event by cloning another event. To do so, click on the "Copy from previous event" button.

## Basic info

The first step is to define the basic information for attendees:

![create event: basic info](/img/event-setup/in-person/001.png) 


### Event Name

Enter the name of the event, which will be displayed during the ticket reservation process and on Tickets.

### Event Organizer

Select the Organizer who will be responsible for this event.

Alf.io is a multi-tenant system by design. This means, that you can manage different organizers on the same instance. This is usually the case for network of conferences. If you've defined only one Organizer, it will be selected by default.


### In person or online event

Alf.io can handle both in-person and online events. For more info on online events, please head to the corresponding [documentation](../create-online-event/)

### Event Location

Enter the address of the event. Since this will be geolocated (see [docs](../../getting-started/first-setup/basicconfig/#map)), please insert it as exact as possible.

The address will be displayed on the reservation page, and also on the ticket. If you have enabled [Apple(tm) Pass integration](../../configuration/wallet-integration/) the location will be used by the Wallet to notify the attendee when they are nearby.

### Event Date

Specify when the event will take place.

### Event time zone

Specify the reference time zone for the event. This is particularly useful if you have international attendees. The default is your browser's time zone.


## Event Description

![create event: description](/img/event-setup/in-person/002.png) 

Add description for your event in one or more languages. To add another language or replace the default (English) click on "Add translation" button. 

This field supports [Markdown](https://commonmark.org/help/).

*please note that the default max length for description is 4000 characters.
 If this does not fit your needs, you can set a new limit using the "Max characters in descriptions" configuration* 

## URLs Configuration

In this section you'll configure the various links that Alf.io will display/use during the reservation process.

![create event: URLs configuration](/img/event-setup/in-person/003.png) 

### Event URL

This defines the event URL on your platform. **Note**: URL is final and cannot be changed after saving the event. 

### Website link

Enter the URL of the event landing page on your website. This is where Alf.io redirects users who don't want to proceed with the booking.

### Terms and Conditions URL

Enter the URL of the Terms and Conditions that attendees have to accept in order to register for your event.

### Privacy policy URL

*optional, but highly recommended*

Enter the URL of the Privacy Policy that attendees have to accept in order to register for your event.


## Logo

Select the logo for your event booking page. 

The image maximum size allowed is 200KB. 
Alf.io currently supports the following formats:

- PNG
- JPG
- GIF
- SVG

## Seats and payment info

In this section you'll define the pricing model of your event and the number of total available seats.

![seats and payment info](/img/event-setup/in-person/004.png)

### Ticket price model

Specify if an admission fee is requested. This will enable or disable the payment subsystem

### Max tickets

The number of available seats, in total. This is usually linked to the venue's maximum capacity. Alf.io does not allow overbooking by design, so it is **guaranteed** that you won't sell/distribute more tickets.

### Regular Price

Enter the full price of your tickets. This is just an indication for future suggestions (e.g. when you create a new ticket category) and is not binding.

### Currency

Enter the ISO-4217 currency code. Type in the name of your currency (e.g. Swiss Francs), and the field will find the code for you.

**Note:** depending on the payment provider you're going to use, the selected currency may not work, or might be converted to your original currency. Please check your payment provider documentation.

### Taxes

If you've to charge VAT or GST for your event, you can specify it here.

### Price includes taxes

Specify whether ticket prices already include taxes, or if Alf.io should add it on top.

### Accepted payment methods

Select the payment methods you will accept for this event. 
Please note that the list of methods here can vary based on what payment providers you have configured.


## Categories

A ticket category is a group of tickets. You may want to define different categories if:

- The ticket price changes over time. E.g. "Blind Bird", "Early Bird", "Full Price"
- You have different prices for members/non-members of your association (see [documentation](../../event-management/groups/))

At least one category is required to sell/distribute tickets. To add it, click on the "Add new" button.

## Define new Category

### Base information

![category: base information](/img/event-setup/in-person/category-001.png)

#### Name

The name of the category. It will be displayed on all generated content for the attendee (reservation page, ticket, e-mail).

#### Description

*optional*

You can define a specific description for each category. This field supports [Markdown](https://commonmark.org/help/).

### Tickets and sales

![category: base information](/img/event-setup/in-person/category-002.png)

#### Visibility

A ticket category can be:

##### **Public**

Everyone can buy/get a ticket for this category. This is the default option.

Some examples:

    - General Admission
    - Early Bird
    - Promotional price

##### **Hidden**

Only a restricted group of people can access and get tickets for this category. Once you create a Hidden category, Alf.io will generate a set of unique codes (one code per seat) that can be used to reserve a ticket for this category.

**Hidden categories need to allocate a fixed number of tickets to work, and therefore they are only compatible with "Fixed number of tickets" ticket allocation strategy**.

Some examples:

    - VIP access
    - Speakers
    - Staff


#### Ticket allocation strategy

Alf.io supports two ticket (seat) allocation strategies:


##### **Grow dynamically**

*this is the default option, and the recommended one for most use cases.*

The category won't allocate tickets exclusively, instead it will share them with other dynamic categories, if any. This is useful if you have only one category or if you have different ways to sell the same seat.

Some examples:

    - Reduced price for association members, full price for external people
    - Event is free of charge

##### **Fixed number of tickets**

Select this option if you want to **reserve** (lock) a fixed number of tickets for this category.

Some examples:

    - Promotional price, limited quantity
    - Tickets for Speakers (hidden category)

#### On sale starting from / until

The sale period for this category, i.e. when people can register.


#### Price

Ticket price for this category.

### Direct reservation link

Reservation Links are a convenient way to initialize one-ticket reservations. If you want to do that, you can define the **Code** here, and Alf.io will show you the complete URL to share.

![category: reservation link](/img/event-setup/in-person/category-003.png)


### Ticket validity

*this is an advanced configuration*

You can set a different validity period for this ticket category. This can be useful for Multi-day events, or if you want to sell a ticket only for a part of the event.

![category: ticket validity](/img/event-setup/in-person/category-004.png)

### Check-in

*this is an advanced configuration*

![category: check-in](/img/event-setup/in-person/category-005.png)

#### Check-in time

You can restrict the period during which a check-in can be done for a particular ticket category. Default is **"At any time"**


#### Badge color

You can link each category to a color, so that you can easily recognize different kinds of ticket holders.
This works with our check-in stations and our mobile app.

For example:

    - VIP access: red
    - Speaker: blue
