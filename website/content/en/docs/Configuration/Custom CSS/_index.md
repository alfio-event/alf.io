---
title: "Custom CSS"
linkTitle: "Custom CSS"
date: 2020-09-02
weight: 11
description: >
  How to define a custom CSS style.
---

From alf.io version 2.0-M3 onward, you can apply your own custom CSS for a more personalized experience.

In the configuration area, there are now 2 textarea for adding your css.

 - the "Base custom css"
 - the "Event custom css"


## Base custom css

This css is loaded in all the pages and can be configured only by users with *ADMIN* role, as it is a System level configuration. This allow you to also customize the "list of available events" page which is not owned by an organization.

## Event custom css

This css is loaded at the event level (all urls under /event/EVENT_NAME/*). You can define this css at System, Organization and Event level: each level will **override** the previous one.

With this css you can customize a single event using the Event level override or for a specific organization.