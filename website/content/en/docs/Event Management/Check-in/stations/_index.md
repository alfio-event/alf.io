---
title: "Check-in Stations (Badge Printing)"
linkTitle: "Badge Printing"
weight: 2
description: >
  How to assemble and configure your check-in stations for check-in and badge printing
---

![Check-in Stations](/img/event-management/check-in/stations.jpg)


{{% pageinfo %}}
Please note that: 

- Check-in stations **require an Alf.io server instance** to download attendees' data and to upload successful scans
- It's not advisable to check-in using check-in stations and our mobile app at the same time. Since the stations can work offline, successful check-ins could be sent to the Alf.io server with a slight delay, whereas the mobile app expects them to be immediately available.

{{% /pageinfo %}}


## Hardware needed

In order to print badges, you'll need the following hardware:

- One or more check-in stations [tutorial](./assemble-station)
- A supported Label Printer, see [details](./supported-printers)
- An USB **QR-Code** (2D) scanner, set to emulate an **US** Keyboard Layout, and to add a **Newline Feed** after the scanned content. This is often the factory settings for these devices

{{% pageinfo %}}
**If you plan to deploy more than one station, you'll need a local network connection, either wired or wireless.**

The stations can work without internet connection after downloading the ticket details, but they need to **communicate with each other** to validate a scan and guarantee that the same ticket can't be scanned on different stations at the same time.
{{% /pageinfo %}}

