---
title: "Set up Cloudflare Turnstile"
linkTitle: "Cloudflare Turnstile"
date: 2024-08-08
weight: 10
description: >
  How to protect from bots with Cloudflare Turnstile
---

## Prerequisites

This feature is available in Alf.io 2.0-M5+

You need Administrator access to Alf.io
You need an active Cloudflare account to generate Turnstile configuration.
If your alf.io instance [is running behind the Cloudflare proxy](https://developers.cloudflare.com/dns/manage-dns-records/reference/proxied-dns-records/) you can configure support for Pre-clearance.

## Create a widget

Please refer to the [official guide](https://developers.cloudflare.com/turnstile/get-started/) to create and configure a Turnstile widget.
You'll need Site Key and Secret Key to configure integration.

## Alf.io integration

Depending on your needs, and whether your alf.io instance [is running behind the Cloudflare proxy](https://developers.cloudflare.com/dns/manage-dns-records/reference/proxied-dns-records/), you can configure alf.io to:

- use a standalone widget
- integrate with [Cloudflare WAF](https://developers.cloudflare.com/waf/)


### Configure Alf.io

Head to Configuration -> System (Administrator access is required)

![Alf.io configuration](/img/configuration/security/turnstile/alfio-configuration.png)

To configure a standalone widget:

- **Enable Cloudflare Turnstile integration** enables / disables integration with Turnstile
- **Cloudflare Turnstile Site Key** is the widget Site Key, as shown in the Cloudflare console
- **Cloudflare Turnstile Secret Key** is the widget Secret Key, as shown in the Cloudflare console

To enable full integration, enable pre-clearance

- **Use Cloudflare pre-clearance** enables full integration with [Cloudflare WAF](https://developers.cloudflare.com/waf/)

### Advanced: Pre-Clearance and WAF rules

### Alf.io configuration

Enable **Use Cloudflare pre-clearance** flag.

### Cloudflare configuration (WAF)

1. On your domain dashboard, select "Security", then "WAF" from the left menu.
2. On the WAF page, select the "Custom rules" tab, then click on "Create rule"
3. Enter a name for your rule, e.g. _alfio-challenge_
4. Click on "Edit expression" and enter the following expression (replace _&lt;alfio-domain>_ with the actual domain):

```
(http.request.uri.path eq "/" and http.host eq "&lt;alfio-domain>")
or (starts_with(http.request.uri.path, "/api/v2/public/event/") and ends_with(http.request.uri.path, "/reserve-tickets") and http.request.method eq "POST")
or (starts_with(http.request.uri.path, "/api/v2/public/subscription/") and http.request.method eq "POST")
```

5. Specify "Managed Challenge" as action
6. Save

### Cloudflare configuration (turnstile)

Go back to your Cloudflare account home, then:

1. Select "Turnstile" from the left menu
1. Click on "Settings" under your widget
1. Opt-in for pre-clearance
1. Select "Managed" as level of pre-clearance
1. Save


