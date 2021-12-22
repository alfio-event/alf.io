---
title: "Event"
linkTitle: "Event"
weight: 1
date: 2020-11-26
description: >
  Compatible Application Events for the "Event" entity
---

<div class="table-responsive">
    <table class="table table-sm table-striped">
        <thead>
            <tr>
                <th rowspan="2">Application Event</th>
                <th colspan="2" class="text-center">Additional global variables</th>
                <th rowspan="2">Expected result type</th>
                <th rowspan="2">About</th>
            </tr>
            <tr>
                <th>Type</th>
                <th>Name</th>
            </tr>
        </thead>
        <tbody>
            <tr>
                <td>EVENT_CREATED</td>
                <td>[`Event`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)</td>
                <td>`event`</td>
                <td>`void`</td>
                <td>Extensions will be invoked asynchronously and synchronously when an event has been created.</td>
            </tr>
            <tr>
                <td rowspan="2">EVENT_STATUS_CHANGE</td>
                <td>[`Event`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)</td>
                <td>`event`</td>
                <td rowspan="2">`void`</td>
                <td rowspan="2">Extensions will be invoked asynchronously and synchronously when an event status changes.</td>
            </tr>
            <tr>
                <td>`Event.Status`</td>
                <td>`status`: possible values are ‘DRAFT’, ‘PUBLIC’ and ‘DISABLED’</td>
            </tr>
            <tr>
                <td rowspan="4">EVENT_METADATA_UPDATE</td>
                <td>[`Event`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)</td>
                <td>`event`</td>
                <td rowspan="4">[`AlfioMetadata`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/metadata/AlfioMetadata.java) </td>
                <td rowspan="4">Extensions will be invoked synchronously when metadata needs to be updated.</td>
            </tr>
            <tr>
                <td>[`AlfioMetadata`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/metadata/AlfioMetadata.java)</td>
                <td>`metadata`</td>
            </tr>
            <tr>
                <td>[`Organization`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/user/Organization.java)</td>
                <td>`organization`</td>
            </tr>
            <tr>
                <td>[`MaybeConfiguration`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/manager/system/ConfigurationManager.java)</td>
                <td>`baseUrl`</td>
            </tr>
        </tbody>
    </table>
</div>