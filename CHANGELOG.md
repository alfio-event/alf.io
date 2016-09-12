# Change Log

## [1.8](https://github.com/exteso/alf.io/tree/1.8) (2016-09-12)
[Full Changelog](https://github.com/exteso/alf.io/compare/1.7.4...1.8)

**Implemented enhancements:**

- create event as draft [\#202](https://github.com/exteso/alf.io/issues/202)
- add ticket PDF when sending message to attendees [\#200](https://github.com/exteso/alf.io/issues/200)
- The manual "Check-In" button only appears when there's a single match [\#196](https://github.com/exteso/alf.io/issues/196)
- Create new event: persist draft during editing [\#184](https://github.com/exteso/alf.io/issues/184)
- Create new event: Ticket form fields, new field [\#181](https://github.com/exteso/alf.io/issues/181)
- Change Password: add warning if admin user [\#180](https://github.com/exteso/alf.io/issues/180)
- Add link to Markdown reference close to the "Preview" button [\#176](https://github.com/exteso/alf.io/issues/176)
- Mailchimp - add event key as user attribute [\#171](https://github.com/exteso/alf.io/issues/171)
- Sold tickets: show number in the dashboard [\#154](https://github.com/exteso/alf.io/issues/154)
- Additional fields: they're hard to see if I buy only one ticket [\#152](https://github.com/exteso/alf.io/issues/152)
- Improve VAT/Price management [\#148](https://github.com/exteso/alf.io/issues/148)
- Logout and Ability to Change Password [\#117](https://github.com/exteso/alf.io/issues/117)
- Assign a discount code to a specific category [\#112](https://github.com/exteso/alf.io/issues/112)
- Donate an arbitrary amount to the event [\#110](https://github.com/exteso/alf.io/issues/110)
- Update spring-boot to 1.3 [\#106](https://github.com/exteso/alf.io/issues/106)
- Add possibility to update validity date for promo code [\#103](https://github.com/exteso/alf.io/issues/103)
- Split full name in first/last name fields [\#102](https://github.com/exteso/alf.io/issues/102)
- Remove Event [\#100](https://github.com/exteso/alf.io/issues/100)
- Paypal integration [\#77](https://github.com/exteso/alf.io/issues/77)
- Markdown support [\#9](https://github.com/exteso/alf.io/issues/9)
- Paypal integration [\#145](https://github.com/exteso/alf.io/pull/145) ([syjer](https://github.com/syjer))

**Fixed bugs:**

- Languages always mandatory in donation options [\#190](https://github.com/exteso/alf.io/issues/190)
- Numeric fields under the “Seats and payment info” allow negative numbers [\#189](https://github.com/exteso/alf.io/issues/189)
- Event begin date cannot be in the past [\#173](https://github.com/exteso/alf.io/issues/173)
- Graphs cannot be drawn when data is empty [\#172](https://github.com/exteso/alf.io/issues/172)
- Change password doesn't work [\#170](https://github.com/exteso/alf.io/issues/170)
- calendar while editing an existing event goes outside the viewport [\#169](https://github.com/exteso/alf.io/issues/169)
- .ics file has an error with new line character [\#168](https://github.com/exteso/alf.io/issues/168)
- Send invitations with CSV uses the wrong language [\#167](https://github.com/exteso/alf.io/issues/167)
- bug in master: cannot add new category in a existing event [\#161](https://github.com/exteso/alf.io/issues/161)
- bug in master: cannot change price of category [\#160](https://github.com/exteso/alf.io/issues/160)
- Cannot edit an event containing strange characters in the url [\#150](https://github.com/exteso/alf.io/issues/150)
- Show contextualized error message when ticket purchase doesn't work [\#147](https://github.com/exteso/alf.io/issues/147)
- Warning message to select an organizer althought it is already selected [\#166](https://github.com/exteso/alf.io/issues/166)
- "ticket-has-changed-owner" email is sent unexpectedly [\#151](https://github.com/exteso/alf.io/issues/151)
- TicketReservationManager.countAvailableTickets count tickets with "PENDING" status as available [\#144](https://github.com/exteso/alf.io/issues/144)

**Closed issues:**

- missing link to created event [\#194](https://github.com/exteso/alf.io/issues/194)
- Editing donation options causes duplication [\#191](https://github.com/exteso/alf.io/issues/191)
- Cannot delete a ticket category [\#188](https://github.com/exteso/alf.io/issues/188)
- Markdown preview: escape HTML [\#178](https://github.com/exteso/alf.io/issues/178)
- Allow markdown rendering to handle no-ops [\#139](https://github.com/exteso/alf.io/issues/139)
- Update Angular $tooltip to $uibTooltip [\#138](https://github.com/exteso/alf.io/issues/138)
- add mariadb in the travis matrix test [\#130](https://github.com/exteso/alf.io/issues/130)
- mysql porting v2 [\#98](https://github.com/exteso/alf.io/issues/98)
- add an additional field while editing an event [\#91](https://github.com/exteso/alf.io/issues/91)
- Mention contributors on the website [\#87](https://github.com/exteso/alf.io/issues/87)
- Updated Tutorial/Instructions [\#74](https://github.com/exteso/alf.io/issues/74)
- "An unexpected error has occurred. Please try again." trying to buy a paid ticket [\#193](https://github.com/exteso/alf.io/issues/193)
- Mysql errror : alter event table not working [\#165](https://github.com/exteso/alf.io/issues/165)
- Unable to create new event without image [\#157](https://github.com/exteso/alf.io/issues/157)
- More info about pending reservations [\#155](https://github.com/exteso/alf.io/issues/155)
- TicketReservationManager.deleteOfflinePayment does not reset categoryId on ticket for dynamic categories [\#146](https://github.com/exteso/alf.io/issues/146)
- Content Security Policy errors with style-src self [\#143](https://github.com/exteso/alf.io/issues/143)
- override general settings with machine-specific settings during development [\#137](https://github.com/exteso/alf.io/issues/137)
- gitignore Mac's `.DS\_Store` file [\#136](https://github.com/exteso/alf.io/issues/136)
- MySQL database setup fails \(invalid default timestamp value\) [\#131](https://github.com/exteso/alf.io/issues/131)
- set max file size for attachments [\#128](https://github.com/exteso/alf.io/issues/128)
- backend Android app:  login failure [\#125](https://github.com/exteso/alf.io/issues/125)

**Merged pull requests:**

- Add AWS Beanstalk support [\#187](https://github.com/exteso/alf.io/pull/187) ([madama](https://github.com/madama))
- Calendar fix [\#183](https://github.com/exteso/alf.io/pull/183) ([yankedev](https://github.com/yankedev))
- \#128 upload file lime 1mb added [\#164](https://github.com/exteso/alf.io/pull/164) ([Praitheesh](https://github.com/Praitheesh))
- split fullname \#102 [\#163](https://github.com/exteso/alf.io/pull/163) ([syjer](https://github.com/syjer))
- fix \#148: Improve VAT/Price management [\#149](https://github.com/exteso/alf.io/pull/149) ([cbellone](https://github.com/cbellone))
- replace data-tooltip with data-uib-tooltip \#138 [\#142](https://github.com/exteso/alf.io/pull/142) ([BunsenMcDubbs](https://github.com/BunsenMcDubbs))
- Allow markdown noop \#139 [\#141](https://github.com/exteso/alf.io/pull/141) ([BunsenMcDubbs](https://github.com/BunsenMcDubbs))
- \#131 \#136 \#137 [\#132](https://github.com/exteso/alf.io/pull/132) ([BunsenMcDubbs](https://github.com/BunsenMcDubbs))


## [1.7.4](https://github.com/exteso/alf.io/tree/1.7.4) (2016-06-29)
[Full Changelog](https://github.com/exteso/alf.io/compare/1.7.3...1.7.4)

**Fixed bugs:**

- "Someting went wrong" message on scan valid QR ticket [\#120](https://github.com/exteso/alf.io/issues/120)

**Closed issues:**

- Getting "invalid API key", not sure which one [\#126](https://github.com/exteso/alf.io/issues/126)
- Event creation issue with 1.7.3 [\#124](https://github.com/exteso/alf.io/issues/124)
- Add change password functionality [\#122](https://github.com/exteso/alf.io/issues/122)
- Cannot create new events [\#119](https://github.com/exteso/alf.io/issues/119)
- Does alf support emitting invoices?  [\#104](https://github.com/exteso/alf.io/issues/104)
- Improve payment form [\#61](https://github.com/exteso/alf.io/issues/61)
- keep an eye on klimpr.com [\#6](https://github.com/exteso/alf.io/issues/6)

**Merged pull requests:**

- add note about install lombok plugin and autowired ide errors [\#129](https://github.com/exteso/alf.io/pull/129) ([BunsenMcDubbs](https://github.com/BunsenMcDubbs))
- \#117 - Add logout functionality [\#121](https://github.com/exteso/alf.io/pull/121) ([pgranato](https://github.com/pgranato))

## [1.7.3](https://github.com/exteso/alf.io/tree/1.7.3) (2016-04-26)
[Full Changelog](https://github.com/exteso/alf.io/compare/1.7.2...1.7.3)

## [1.7.2](https://github.com/exteso/alf.io/tree/1.7.2) (2016-04-21)
[Full Changelog](https://github.com/exteso/alf.io/compare/1.7.2-RC1...1.7.2)

**Fixed bugs:**

- Login issues on Heroku [\#116](https://github.com/exteso/alf.io/issues/116)

## [1.7.2-RC1](https://github.com/exteso/alf.io/tree/1.7.2-RC1) (2016-04-08)
[Full Changelog](https://github.com/exteso/alf.io/compare/1.7.1...1.7.2-RC1)

**Implemented enhancements:**

- New API for sponsors [\#107](https://github.com/exteso/alf.io/issues/107)

**Fixed bugs:**

- MySql - invalid statement \(syntax error\) [\#108](https://github.com/exteso/alf.io/issues/108)

**Closed issues:**

- Cannot create event [\#113](https://github.com/exteso/alf.io/issues/113)
- Running behind proxy? [\#105](https://github.com/exteso/alf.io/issues/105)

## [1.7.1](https://github.com/exteso/alf.io/tree/1.7.1) (2016-02-16)
[Full Changelog](https://github.com/exteso/alf.io/compare/1.7...1.7.1)

**Fixed bugs:**

- regression: cloud foundry support is broken [\#101](https://github.com/exteso/alf.io/issues/101)

## [1.7](https://github.com/exteso/alf.io/tree/1.7) (2016-02-13)
[Full Changelog](https://github.com/exteso/alf.io/compare/1.6.2...1.7)

**Implemented enhancements:**

- mysql porting [\#90](https://github.com/exteso/alf.io/issues/90)

**Closed issues:**

- simplify/fix email handling [\#93](https://github.com/exteso/alf.io/issues/93)

## [1.6.2](https://github.com/exteso/alf.io/tree/1.6.2) (2016-01-28)
[Full Changelog](https://github.com/exteso/alf.io/compare/1.6.1...1.6.2)

**Closed issues:**

- Video source is empty in online checkin [\#96](https://github.com/exteso/alf.io/issues/96)
- Search [\#94](https://github.com/exteso/alf.io/issues/94)

**Merged pull requests:**

- Dutch lang [\#95](https://github.com/exteso/alf.io/pull/95) ([mg-1999](https://github.com/mg-1999))

## [1.6.1](https://github.com/exteso/alf.io/tree/1.6.1) (2015-11-28)
[Full Changelog](https://github.com/exteso/alf.io/compare/1.6...1.6.1)

## [1.6](https://github.com/exteso/alf.io/tree/1.6) (2015-11-22)
[Full Changelog](https://github.com/exteso/alf.io/compare/1.5.2...1.6)

**Implemented enhancements:**

- REST APIs [\#86](https://github.com/exteso/alf.io/issues/86)
- Create link to external events [\#85](https://github.com/exteso/alf.io/issues/85)
- GUI support for multitenancy [\#62](https://github.com/exteso/alf.io/issues/62)
- support generic input/textarea field when assigning a ticket [\#46](https://github.com/exteso/alf.io/issues/46)

**Fixed bugs:**

- PostSQL error [\#82](https://github.com/exteso/alf.io/issues/82)
- Cant load scan page video source [\#79](https://github.com/exteso/alf.io/issues/79)

**Closed issues:**

- lock the ticket during check-in process [\#89](https://github.com/exteso/alf.io/issues/89)
- Add "express checkout" option [\#55](https://github.com/exteso/alf.io/issues/55)

## [1.5.2](https://github.com/exteso/alf.io/tree/1.5.2) (2015-10-20)
[Full Changelog](https://github.com/exteso/alf.io/compare/1.5.1...1.5.2)

**Closed issues:**

- Language doesn't transfer when switching page. [\#80](https://github.com/exteso/alf.io/issues/80)
- Add new language [\#78](https://github.com/exteso/alf.io/issues/78)
- Cant insert address [\#76](https://github.com/exteso/alf.io/issues/76)

**Merged pull requests:**

- Add dutch language [\#83](https://github.com/exteso/alf.io/pull/83) ([mg-1999](https://github.com/mg-1999))

## [1.5.1](https://github.com/exteso/alf.io/tree/1.5.1) (2015-09-22)
[Full Changelog](https://github.com/exteso/alf.io/compare/1.5...1.5.1)

## [1.5](https://github.com/exteso/alf.io/tree/1.5) (2015-09-16)
[Full Changelog](https://github.com/exteso/alf.io/compare/1.4.1...1.5)

**Implemented enhancements:**

- Add docker support to alf.io [\#65](https://github.com/exteso/alf.io/issues/65)
- Generate and attach pdf payment receipt to confirmation email \(only when the user has effectively paid\) [\#54](https://github.com/exteso/alf.io/issues/54)
- In the qrcode for the check in operator, expose the full site info [\#53](https://github.com/exteso/alf.io/issues/53)
- Create "catch all" categories [\#50](https://github.com/exteso/alf.io/issues/50)
- put the qrcode in the upper right corner of the pdf [\#48](https://github.com/exteso/alf.io/issues/48)
- add image upload support for event logo [\#47](https://github.com/exteso/alf.io/issues/47)
- support add to calendar feature [\#43](https://github.com/exteso/alf.io/issues/43)
- support waiting queue [\#39](https://github.com/exteso/alf.io/issues/39)
- support multi language event and ticket category description [\#38](https://github.com/exteso/alf.io/issues/38)
- Add event name [\#35](https://github.com/exteso/alf.io/issues/35)

**Closed issues:**

- Error when using "Send custom message..." [\#72](https://github.com/exteso/alf.io/issues/72)
- Can not cancel paid ticket [\#71](https://github.com/exteso/alf.io/issues/71)
- Bug creating new organizations [\#70](https://github.com/exteso/alf.io/issues/70)
- Error while doing the environment setup [\#57](https://github.com/exteso/alf.io/issues/57)
- Create settings.properties [\#52](https://github.com/exteso/alf.io/issues/52)
- Export ticket details [\#42](https://github.com/exteso/alf.io/issues/42)
- add quartz scheduler for cluster aware job [\#73](https://github.com/exteso/alf.io/issues/73)
- translations [\#68](https://github.com/exteso/alf.io/issues/68)
- Cookie-law compliance [\#67](https://github.com/exteso/alf.io/issues/67)
- GUI UX/UI redesign [\#66](https://github.com/exteso/alf.io/issues/66)
- support multi tenancy [\#56](https://github.com/exteso/alf.io/issues/56)
- add mailchimp integration [\#36](https://github.com/exteso/alf.io/issues/36)

**Merged pull requests:**

- Improved translations [\#75](https://github.com/exteso/alf.io/pull/75) ([patbaumgartner](https://github.com/patbaumgartner))
- Ajusted translation from SIE to DU, translated  new text blocks [\#69](https://github.com/exteso/alf.io/pull/69) ([patbaumgartner](https://github.com/patbaumgartner))
- German translation [\#64](https://github.com/exteso/alf.io/pull/64) ([patbaumgartner](https://github.com/patbaumgartner))
- 1.3 maintenance [\#49](https://github.com/exteso/alf.io/pull/49) ([apolci](https://github.com/apolci))

## [1.4.1](https://github.com/exteso/alf.io/tree/1.4.1) (2015-04-07)
[Full Changelog](https://github.com/exteso/alf.io/compare/1.4...1.4.1)

## [1.4](https://github.com/exteso/alf.io/tree/1.4) (2015-04-07)
[Full Changelog](https://github.com/exteso/alf.io/compare/1.4-RC2...1.4)

**Implemented enhancements:**

- Shrink active category [\#45](https://github.com/exteso/alf.io/issues/45)
- reminder e-mail before event [\#24](https://github.com/exteso/alf.io/issues/24)
- check-in application [\#18](https://github.com/exteso/alf.io/issues/18)

**Fixed bugs:**

- \(admin\) Fix default Payment Type [\#44](https://github.com/exteso/alf.io/issues/44)

**Closed issues:**

- Show ticket details on admin page [\#41](https://github.com/exteso/alf.io/issues/41)

## [1.4-RC2](https://github.com/exteso/alf.io/tree/1.4-RC2) (2015-04-06)
[Full Changelog](https://github.com/exteso/alf.io/compare/alfio-1.3.3...1.4-RC2)

## [alfio-1.3.3](https://github.com/exteso/alf.io/tree/alfio-1.3.3) (2015-03-07)
[Full Changelog](https://github.com/exteso/alf.io/compare/alfio-1.3.2...alfio-1.3.3)

**Implemented enhancements:**

- add https://coveralls.io/ integration [\#19](https://github.com/exteso/alf.io/issues/19)

## [alfio-1.3.2](https://github.com/exteso/alf.io/tree/alfio-1.3.2) (2015-03-06)
[Full Changelog](https://github.com/exteso/alf.io/compare/alfio-1.3.1...alfio-1.3.2)

## [alfio-1.3.1](https://github.com/exteso/alf.io/tree/alfio-1.3.1) (2015-03-01)
[Full Changelog](https://github.com/exteso/alf.io/compare/alfio-1.3...alfio-1.3.1)

## [alfio-1.3](https://github.com/exteso/alf.io/tree/alfio-1.3) (2015-02-28)
[Full Changelog](https://github.com/exteso/alf.io/compare/alfio-1.3-beta1...alfio-1.3)

**Implemented enhancements:**

- redesign/refactoring of configuration GUI [\#16](https://github.com/exteso/alf.io/issues/16)
- Create email sending queue [\#13](https://github.com/exteso/alf.io/issues/13)

**Closed issues:**

- export attendees' data [\#37](https://github.com/exteso/alf.io/issues/37)
- send invitation e-mail with reserved code [\#34](https://github.com/exteso/alf.io/issues/34)
- REST API for checkin app [\#25](https://github.com/exteso/alf.io/issues/25)

**Merged pull requests:**

- Added gradle support. [\#33](https://github.com/exteso/alf.io/pull/33) ([aalmiray](https://github.com/aalmiray))

## [alfio-1.3-beta1](https://github.com/exteso/alf.io/tree/alfio-1.3-beta1) (2015-01-18)
[Full Changelog](https://github.com/exteso/alf.io/compare/alfio-1.2...alfio-1.3-beta1)

**Fixed bugs:**

- Validation is not triggered on page load [\#10](https://github.com/exteso/alf.io/issues/10)

**Closed issues:**

- Update category name doesn't work [\#32](https://github.com/exteso/alf.io/issues/32)

## [alfio-1.2](https://github.com/exteso/alf.io/tree/alfio-1.2) (2015-01-13)
[Full Changelog](https://github.com/exteso/alf.io/compare/alfio-1.1...alfio-1.2)

**Implemented enhancements:**

- promo codes [\#29](https://github.com/exteso/alf.io/issues/29)

**Fixed bugs:**

- updating the price of a ticket category update only one of the two price related column in the ticket entity [\#31](https://github.com/exteso/alf.io/issues/31)
- Wrong price percentage calculation when creating an event with VAT excluded [\#30](https://github.com/exteso/alf.io/issues/30)

## [alfio-1.1](https://github.com/exteso/alf.io/tree/alfio-1.1) (2014-12-31)
[Full Changelog](https://github.com/exteso/alf.io/compare/alfio-1.0...alfio-1.1)

**Implemented enhancements:**

- Manual payment processing [\#22](https://github.com/exteso/alf.io/issues/22)
- use mailgun's REST apis [\#15](https://github.com/exteso/alf.io/issues/15)
- In admin, event page: sort token by use, name, for ticket, sort by time [\#11](https://github.com/exteso/alf.io/issues/11)

**Fixed bugs:**

- reassign ticket from a restricted category to another one [\#26](https://github.com/exteso/alf.io/issues/26)

**Closed issues:**

- complete CSP headers [\#28](https://github.com/exteso/alf.io/issues/28)
- split process URLs [\#27](https://github.com/exteso/alf.io/issues/27)
- Support asynchronous payment flows [\#23](https://github.com/exteso/alf.io/issues/23)
- Generate accessible ticket PDF [\#2](https://github.com/exteso/alf.io/issues/2)

## [alfio-1.0](https://github.com/exteso/alf.io/tree/alfio-1.0) (2014-12-14)
[Full Changelog](https://github.com/exteso/alf.io/compare/v1.0-pre-rename-v5...alfio-1.0)

**Implemented enhancements:**

- Add "additional info for organizer" [\#21](https://github.com/exteso/alf.io/issues/21)
- insert "expired on..." on expired categories [\#17](https://github.com/exteso/alf.io/issues/17)
- Partial editing of event [\#14](https://github.com/exteso/alf.io/issues/14)

**Fixed bugs:**

- TicketRepository.freeFromReservation does not clear up special\_price\_id\_fk column [\#12](https://github.com/exteso/alf.io/issues/12)

**Closed issues:**

- check the .sql creation script and add the missing index [\#20](https://github.com/exteso/alf.io/issues/20)
- Create admin area [\#1](https://github.com/exteso/alf.io/issues/1)

## [v1.0-pre-rename-v5](https://github.com/exteso/alf.io/tree/v1.0-pre-rename-v5) (2014-11-14)
[Full Changelog](https://github.com/exteso/alf.io/compare/v1.0-pre-rename-v4...v1.0-pre-rename-v5)

## [v1.0-pre-rename-v4](https://github.com/exteso/alf.io/tree/v1.0-pre-rename-v4) (2014-11-11)
[Full Changelog](https://github.com/exteso/alf.io/compare/v1.0-pre-rename-v3...v1.0-pre-rename-v4)

## [v1.0-pre-rename-v3](https://github.com/exteso/alf.io/tree/v1.0-pre-rename-v3) (2014-11-10)
[Full Changelog](https://github.com/exteso/alf.io/compare/v1.0-pre-rename-v2...v1.0-pre-rename-v3)

## [v1.0-pre-rename-v2](https://github.com/exteso/alf.io/tree/v1.0-pre-rename-v2) (2014-11-10)
[Full Changelog](https://github.com/exteso/alf.io/compare/v1.0-pre-rename...v1.0-pre-rename-v2)

## [v1.0-pre-rename](https://github.com/exteso/alf.io/tree/v1.0-pre-rename) (2014-11-09)
**Implemented enhancements:**

- allow free event creation [\#5](https://github.com/exteso/alf.io/issues/5)

**Closed issues:**

- Handle correctly the timezone of a event [\#8](https://github.com/exteso/alf.io/issues/8)
- https handling [\#7](https://github.com/exteso/alf.io/issues/7)
- configure payment methods [\#4](https://github.com/exteso/alf.io/issues/4)
- integrate stripe.com [\#3](https://github.com/exteso/alf.io/issues/3)



\* *This Change Log was automatically generated by [github_changelog_generator](https://github.com/skywinder/Github-Changelog-Generator)*
