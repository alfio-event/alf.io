---
title: "Event"
linkTitle: "Event"
weight: 1
date: 2020-11-26
description: >
  Compatible Application Events for the "Event" entity
---
### Event created
`EVENT_CREATED`

Fired both **asynchronously** and **synchronously** when an Event is created 
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
                <td>`event`</td>
                <td>[`Event`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Event.java)</td>
                <td>The created Event</td>
            </tr>
        </tbody>
    </table>
</div>

### Event created
`EVENT_VALIDATE_CREATION`

Fired **synchronously** before creating an Event. Useful to perform custom validations
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
                <td>`request`</td>
                <td>[`EventModification`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/modification/EventModification.java)</td>
                <td>Creation request</td>
            </tr>
        </tbody>
    </table>
</div>

### Event status change
`EVENT_STATUS_CHANGE`

Fired both **asynchronously** and **synchronously** when an Event status changes 
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
                <td>`event`</td>
                <td>[`Event`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Event.java)</td>
                <td>The Event</td>
            </tr>
            <tr>
                <td>`status`</td>
                <td>[`Event.Status`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Event.java#L45)</td>
                <td>updated Status</td>
            </tr>
        </tbody>
    </table>
</div>

### Validate seats/prices update
`EVENT_VALIDATE_SEATS_PRICES_UPDATE`

Fired **synchronously** when a modification to prices and/or seats number is requested for an Event
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
                <td>`event`</td>
                <td>[`Event`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Event.java)</td>
                <td>The Event</td>
            </tr>
            <tr>
                <td>`request`</td>
                <td>[`EventModification`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/modification/EventModification.java)</td>
                <td>Modification request</td>
            </tr>
        </tbody>
    </table>
</div>

### Event status change
`EVENT_METADATA_UPDATE`

Fired **synchronously** when the organizer updates the metadata of an Event.

A result of type [`AlfioMetadata`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/metadata/AlfioMetadata.java) is expected. Return `null` if you want to fall back to default settings. 
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
                <td>`event`</td>
                <td>[`Event`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Event.java)</td>
                <td>The Event</td>
            </tr>
            <tr>
                <td>`metadata`</td>
                <td>[`AlfioMetadata`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/metadata/AlfioMetadata.java)</td>
                <td>existing metadata. **Might be `null`**</td>
            </tr>
            <tr>
                <td>`organization`</td>
                <td>[`Organization`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/user/Organization.java)</td>
                <td>organizer details</td>
            </tr>
            <tr>
                <td>`baseUrl`</td>
                <td>String</td>
                <td>The configured "Base URL" for the current organizer</td>
            </tr>
        </tbody>
    </table>
</div>
