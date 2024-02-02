---
title: "Extensions reference"
linkTitle: "Reference"
weight: 2
date: 2020-11-26
description: >
  Reference documentation for Extensions development
---

# Alf.io extensions

The official repository for the extensions can be found [here](https://github.com/alfio-event/alf.io-extensions).

# How to write an extension

Extensions allow you to link Alf.io with your existing tools, such as:

* Billing/Accounting systems
* CRMs
* Additional Email marketing services (Mailjet, ...)
* Custom notifications (Slack, Telegram, etc.)

## How it works

Extensions can be added and modified by each user. However, some limitations are applied that can be found
[here](https://alf.io/docs/reference/extensions/introduction/#alf-io-extensions-language).

Each extension consists of a JavaScript script and is registered to one or more Application Events, 
and is fired as soon as the Application Event occurs.

You can find some sample code in the [introduction](https://alf.io/docs/reference/extensions/introduction/#example-of-a-working-script) page.

## Scope Variables

Alf.io provides some objects and properties to the script in the script scope:
<div class="table-responsive table-hover">
    <table class="table table-sm">
        <thead>
            <tr>
                <th>Variable</th>
                <th>Type</th>
                <th>About</th>
            </tr>
        </thead>
        <tbody>
            <tr>
                <td>`log`</td>
                <td>`Log4j`</td>
                <td>Logging utility</td>
            </tr>
            <tr>
                <td>`extensionLogger`</td>
                <td>[`ExtensionLogger`] ( https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/extension/ExtensionLogger.java)</td>
                <td>A logger that writes in the extension_log table.</td>
            </tr>
            <tr>
                <td>`simpleHttpClient`</td>
                <td>[`SimpleHttpClient`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/extension/SimpleHttpClient.java)</td>
                <td>A simplified version created by Alf.io for calling external services</td>
            </tr>
            <tr>
                <td>`GSON`</td>
                <td>[`GSON`](https://github.com/google/gson)</td>
                <td>JSON parser/generator</td>
            </tr>
            <tr>
                <td>`returnClass`</td>
                <td>`java.lang.Class<?>`</td>
                <td>The return class of the methods</td>
            </tr>
            <tr>
                <td>`extensionParameters`</td>
                <td>`Map<String, Object>`</td>
                <td>Defined parameters as in the script metadata</td>
            </tr>
            <tr>
                <td>`event`</td>
                <td>[`Event`]( https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Event.java)</td>
                <td>Alf.io's implementation of a general event</td>
            </tr>
            <tr>
                <td>`eventId`</td>
                <td>`int`</td>
                <td>The id of the event</td>
            </tr>
            <tr>
                <td>`organizationId`</td>
                <td>`int`</td>
                <td>The id of the organization that organized the event </td>
            </tr>
            <tr>
                <td>`Utils`</td>
                <td>[`ExtensionUtils`]( https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/extension/ExtensionUtils.java)</td>
                <td>A collection of utilities</td>
            </tr>
            <tr>
                <td>`executionKey`</td>
                <td>`String`</td>
                <td>An identifier for the execution, which can be treated as an [idempotency key](https://stripe.com/blog/idempotency).</td>
            </tr>
        </tbody>
    </table>
</div>
       
Other event-related variables are also injected in the scope.

## Methods

#### `getScriptMetadata`

This method returns the actual configuration options and capabilities of the extension.
It **must** return a `JSON` object with the following properties:

* async `boolean`: whether or not the script should be invoked asynchronously.
* events `string[]`: list of supported events
* configuration {(key: `string`): `string`}: the extension configuration (WIP)

#### `executeScript`

The actual event handling. Return types are event-dependent. Will always receive a single parameter (`scriptEvent`) 
which is the event that triggered the script.

## Application Events by entity

Below is a list of all the supported Application Events by related entity, with some additional global variables, expected result type and a short explanation.