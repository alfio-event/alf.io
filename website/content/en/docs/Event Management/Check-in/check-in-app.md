---
title: "Mobile App"
linkTitle: "Mobile App"
weight: 1
description: >
  Learn How To Check-in Attendees or Guests in Alf.io using the mobile app (iOS/Android)
---

{{% pageinfo %}}
Please note that the check-in app needs an active internet connection in order to validate the tickets. 
{{% /pageinfo %}}

<div class="row">
<div class="col-3 offset-3">
   <a href="https://play.google.com/store/apps/details?id=alfio.scan"><img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" style="height: auto; width: 200px;"></a>
</div>
<div class="col-3">
    <a href="https://itunes.apple.com/us/app/alf-io-scan/id1425492093" style="display:inline-block;overflow:hidden;background:url(https://linkmaker.itunes.apple.com/assets/shared/badges/en-us/appstore-lrg.svg) no-repeat;width:180px;height:60px;background-size:contain;margin-top:9px;"></a>
</div>
</div>

## Create the account

### Create Check-in operator account

Go to the admin section of your alf.io instance, in the 
"Api Keys" tab, create a new api key and grant it the "Check-in 
Operator" role

![create account](/img/tutorials/check-in-app/001.png)


### Display QR Code

Once the api key has been generated, click the "View QR Code" button to display it.

![scan qr code](/img/tutorials/check-in-app/002.png)



## Configure the App

### Scan the QR Code

Click on the "+" button, and then scan the QR Code.

![scan qr code](/img/tutorials/check-in-app/003.png)

### Select the Account

Once scanned, the account will appear in the list, click on it to select it

![select account](/img/tutorials/check-in-app/004.png)


### Select the event

You will be asked to select one event from the list.

![select event](/img/tutorials/check-in-app/005.png)


## Scan attendees' tickets

### Init check-in

Click on "Scan Attendees" button in order to start the check-in process.

![select event](/img/tutorials/check-in-app/006.png)


## Possible scan results

Vibration feedback: once means "successful", three times: "error / action required"


### Check-in successful

Everything's OK, the Attendee can enjoy the event

### Attendee must pay the ticket upon arrival

An outstanding payment has been found. Once it has been cleared out, click on "Confirm" to perform check-in

### Ticket already checked in
  
A ticket can be checked-in only once. This error doesn't necessarily mean that the attendee is trying to cheat.  
You have to verify if their ticket is part of a group reservation, maybe they are just showing the wrong one.


### Ticket not found
  
The system is unable to find a match for the scanned ticket, could be one of the following:
  
1. You have selected the wrong event from the list
2. The Attendee has modified the ticket (e.g. updated the email address) and is showing the old one
3. The Attendee has reassigned the ticket to someone else and is trying to access the event anyway
