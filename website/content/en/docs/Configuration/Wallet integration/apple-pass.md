---
title: "Apple (tm) Pass Integration"
linkTitle: "Apple (tm) Pass"
weight: 5
description: >
    How to generate Apple(tm) passes for your tickets
---

## How to configure Apple (tm) Pass integration

### create a new Apple (tm) Pass ID

access the [create Pass ID](https://developer.apple.com/account/ios/identifier/passTypeId/create) page and follow the on-screen instruction to generate your new Pass ID and the corresponding certificates.

### Download the certificate on your disk

download the certificate on your disk, as suggested during the generation process, then double-click on it to install in Keychain Access

### Export certificate

make sure to select "My Certificates" under "Category", as shown by the screenshot below

![](/img/configuration/apple-pass/category.png)

Select your pass from the list

![](/img/configuration/apple-pass/export.png)

open context menu, then select "Export Pass ..." and export it as P12 file.
Please make sure to enter a strong password, you'll need it afterwards.

### Import certificate in alf.io

open a terminal, go to the folder where the exported P12 is, then execute the following command:

> $ base64 certificate.p12

and copy the output.

then open you alf.io instance and set the relevant options:

![](/img/configuration/apple-pass/alfio-options.png)

that's it! Enjoy Apple (tm) Pass integration!