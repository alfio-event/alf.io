---
title: "Assemble your own check-in station"
linkTitle: "Build Station"
weight: 2
description: >
  How to build and configure your check-in stations for check-in and badge printing
---

## Hardware

[Alf.io-PI](https://github.com/alfio-event/alf.io-PI) is the software which powers our check-in stations. As the name might suggest, it has been designed to run on a [Raspberry-PI](https://www.raspberrypi.org/)

In this tutorial we will be using the following hardware:

- 1x [Raspberry Pi 3 Model B+](https://www.raspberrypi.org/products/raspberry-pi-3-model-b-plus/). Please note that at the time of writing, RaspberryPi 4 is not yet supported
- 1x [Raspberry Pi 7” Touchscreen Display](https://www.raspberrypi.org/products/raspberry-pi-touch-display/);
- 1x [USB power supply](https://www.raspberrypi.org/products/raspberry-pi-universal-power-supply/) to power both the Raspberry and the Touchscreen. Advice: buy the official one, or at least one that can deliver 2.5A.
- 1x microSD class 10 card with a capacity of at least 16GB
- ... and a case able to fit both the Touchscreen and the Rasperry.


The very first time you setup the Rasberry you should also have:

- 1x Phillips 0 screwdriver;
- 1x USB keyboard;
- 1x USB mouse;
- A stable connection to the internet;
- ... and your laptop "just in case".

If you are not too much confident and you want to proceed by little, incremental steps, you also might want to have an HDMI screen with its cable "ready to go". This will allow you to verify if the Raspberry is working fine, deploy Raspian and then adding the 7" touchscreen display. 

## Deploying Raspian on your microSD card

We currently support Raspbian Stretch (Debian 9). 

- download the image from [this link](https://downloads.raspberrypi.org/raspbian/images/raspbian-2019-04-09/2019-04-08-raspbian-stretch.zip) or [use bittorrent](https://downloads.raspberrypi.org/raspbian/images/raspbian-2019-04-09/2019-04-08-raspbian-stretch.zip.torrent)
- install the image on a clean SD card by following [the official guide](https://www.raspberrypi.org/documentation/installation/installing-images/)
- make sure that all settings dtoverlay= are commented out in config.txt, and add dtoverlay=rpi-ft5406 at the end of the file

## Assembling the entire thing

Now that you have deployed Raspbian on your microSD card, we are finally able to assemble the Rasperry, the touchscreen and the microSD card in the case.

> Disclaimer: The instructions here do not replace the one provided with the hardware you bought. You should refer to them when assembling your own configuration.

Here's the (very shortened) sequences of steps to follow:

1. Be sure to have inserted the microSD card in the Raspberry PI;
1. Mount your Touchscreen on top of the Raspberry as described [here](https://www.element14.com/community/docs/DOC-78156/l/raspberry-pi-7-touchscreen-display). This is the most delicate part, so take your time;
1. Place everything in the case and tighten the screws;
1. Done!

## Fire it up!

Now that you have assembled the entire thing, it's finally time to start the Raspberry to ensure that everything is working correctly. To do that, simply connect your keyboard, mouse and USB power supply to your Raspberry. You should then see the Raspian boot screen and, after a while, you should be inside the operating system.

### First time configuration

The first time you enter in Raspbian, you might be asked to configure your language, keyboard layout and connection to the internet. Simply choose your settings and then hit finish.

### Rotate the screen, if needed

Depending on the case that you have choosen, it might be necessary to rotate both the screen and the touchscreen. 

If you need to rotate the screen and/or the touchscreen, run a Terminal and type the following command:

```
sudo nano /boot/config.txt
```

Then scroll all the way to the end of the file and add

```
# Rotate the screen by 180 degrees
screen_rotate=2
```

or

```
# Rotate both the screen and the LCD by 180 degrees
lcd_rotate=2
```

Then save your document with `CTRL+O` and then exit with `CTRL+X`. Reboot your Raspberry to verify if the new setting is correctly applied.

### Enable SSH (Secure Shell)

This section is dedicated to more experienced users, that sometime might find more convenient to access the Raspberry operating system directly from a laptop, instead of using the external keyboard, mouse and display. To do that, you need to enable the SSH as explained [here](https://www.raspberrypi.org/documentation/remote-access/ssh/), because this feature is turned off by default for security reasons.

## Install Alf.io-PI

Installing the software on a Raspberry Pi 3B+ has been extremly simplified with the release of a script, that download and install all the necessary bits directly from the internet on your device.

Just open a new terminal and paste the following line

```bash
$ curl -L https://github.com/alfio-event/alf.io-PI/releases/download/v0.9.9/get-alfio-pi.sh | bash
```

then, wait for the magic to happen. Once finished the install, please follow the on-screen instructions and configure your alf.io https address as well as your credentials. 

After that, please restart your Raspberry-PI as suggested.

## Finally, Alf.io-PI runs on your Raspberry-PI

When powering on the Raspberry, Alf.io-PI is automatically executed and you can start welcoming your guest at your own event reading the ticket that have been **proudly generated by Alf.io, the open source ticket reservation system**.

## Credits

This guide has been originally written by [Michel Primo](https://twitter.com/mfprimo)