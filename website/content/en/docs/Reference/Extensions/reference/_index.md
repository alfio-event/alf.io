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
    <table class="table table-sm td-initial">
        <thead>
            <tr>
                <th>Variable</th>
                <th>Type</th>
                <th>About</th>
            </tr>
        </thead>
        <tbody>
            <tr>
                <td><code>log</code></td>
                <td><code>Log4j</code></td>
                <td>Logging utility</td>
            </tr>
            <tr>
                <td><code>extensionLogger</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/extension/ExtensionLogger.java" target="_blank" rel="noopener"><code>ExtensionLogger</code></a></td>
                <td>A logger that writes in the extension_log table.</td>
            </tr>
            <tr>
                <td><code>simpleHttpClient</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/extension/SimpleHttpClient.java" target="_blank" rel="noopener"><code>SimpleHttpClient</code></a></td>
                <td>A simplified version created by Alf.io for calling external services</td>
            </tr>
            <tr>
                <td><code>GSON</code></td>
                <td><a href="https://github.com/google/gson" target="_blank" rel="noopener"><code>GSON</code></a></td>
                <td>JSON parser/generator</td>
            </tr>
            <tr>
                <td><code>returnClass</code></td>
                <td><code>java.lang.Class&lt;?&gt;</code></td>
                <td>The return class of the methods</td>
            </tr>
            <tr>
                <td><code>extensionParameters</code></td>
                <td><code>Map&lt;String, Object&gt;</code></td>
                <td>Defined parameters as in the script metadata</td>
            </tr>
            <tr>
                <td><code>event</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Event.java" target="_blank" rel="noopener"><code>Event</code></a></td>
                <td>Alf.io's implementation of a general event</td>
            </tr>
            <tr>
                <td><code>eventId</code></td>
                <td><code>int</code></td>
                <td>The id of the event</td>
            </tr>
            <tr>
                <td><code>organizationId</code></td>
                <td><code>int</code></td>
                <td>The id of the organization that organized the event </td>
            </tr>
            <tr>
                <td><code>Utils</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/extension/ExtensionUtils.java" target="_blank" rel="noopener"><code>ExtensionUtils</code></a></td>
                <td>A collection of utilities</td>
            </tr>
            <tr>
                <td><code>executionKey</code></td>
                <td><code>String</code></td>
                <td>An identifier for the execution, which can be treated as an <a href="https://stripe.com/blog/idempotency" target="_blank" rel="noopener">idempotency key</a>.</td>
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