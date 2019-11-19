---
title: "Mailgun"
linkTitle: "Mailgun"
weight: 5
date: 2019-10-14
description: >
  How to configure alf.io to send e-mails using Mailgun APIs

---

[Mailgun](https://mailgun.com) is an US/EU-Based transactional email service.

Please refer to the [official documentation](https://help.mailgun.com/hc/en-us/articles/203380100-Where-can-I-find-my-API-key-and-SMTP-credentials-) for details about the API Keys generation

![mailgun options](/img/getting-started/email/mailgun-email-options.PNG)

### GDPR Compliance

If you are subject to GDPR, please make sure to create your account [on their EU region](https://www.mailgun.com/blog/we-have-a-new-region-in-europe-yall/).

Then enable the **Use Mailgun EU region** flag. This will instruct alf.io to call Mailgun's EU API endpoint.