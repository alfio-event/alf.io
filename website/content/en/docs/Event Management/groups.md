---
title: "How to limit access to a public ticket Category"
linkTitle: "Limit access to a public Category"
weight: 4
description: >
  How to limit access to a public Category to a group of members
---

## How to limit access to a public category

If you want to have a ticket category that is public (that is, visible for everybody) but whose tickets can be acquired/bought only from a group of people, you have to configure and use **groups** and limit access only to a group of **email addresses** or **internet domains**. 

*Here an example use case:*

Let's say that you want to add a special discount (-50%) for students for your event. 

In order to access the discounted price, students have to buy the tickets using their university's email address.

### Step 1: create a group

click on the "Groups " menu

![groups-menu](/img/tutorials/groups/groups-menu.webp) 

Create a new group. You can enter addresses one by one, or you can upload a CSV files.

In the example below, we are entering the email address info@students.myuniversity.com, which we'll use later on for activating a domain-based match on **students.myuniversity.com**

![create-group](/img/tutorials/groups/create-group.webp)



after you have entered the email addresses, one for each domain you want to allow, click on **Save**.



In this example, we want to allow the following email address:

- name1@**students.myuniversity.com**

- name2@**students.myuniversity.com**

- name3@**students.myuniversity.com**

and deny the following:

- professor1@***dep1.myuniversity.com***

- professor2@***dep2.myuniversity.com***

- professor3@***myuniversity.com***



### Step 2: link the group to a category

Head back to the event detail page, find the "Students" category (or create a new one if you don't have it yet) and click on the "options" button

![options-button](/img/tutorials/groups/options-button.webp)



Then link the group as follows and save the configuration:

![link-to-group](/img/tutorials/groups/link-to-group.webp)

where:

**Match Type** is the type of match between the ticket holder and the group member. At the moment, the following matches are supported:

- **Full Match**: the email address in the group must match exactly the email address of the ticket holder

- **Match full email address, fallback on domain**: is a "by example" match. This is what we'll choose for our example, as we want to allow all the addresses on *students.myuniversity.com* to buy a ticket

**Ticket Allocation Strategy**: is a setting that controls how many tickets can be registered to a group member. 

- **Unlimited**: no limits on the number of tickets

- **Limit to one ticket per email address**: maximum one ticket per group member

- **Limit to a specific number of tickets per email address**: specify the maximum of tickets allowed per group member

once saved, reload the event page and you'll see the new setting:

![group-active](/img/tutorials/groups/group-active.webp)

enjoy!
