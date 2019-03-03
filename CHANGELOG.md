# Change Log

## [2.0-M1](https://github.com/alfio-event/alf.io/tree/2.0-M1) (2019-01-30)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/2.0-M0-EINVOICE-2...2.0-M1)

**Implemented enhancements:**

- enable by default the alf.io PI support [\#591](https://github.com/alfio-event/alf.io/issues/591)
- Invoice / Receipt pdf output: write it directly to the outputstream instead of keeping it in memory [\#578](https://github.com/alfio-event/alf.io/issues/578)
- Implement italian e-invoicing support [\#573](https://github.com/alfio-event/alf.io/issues/573)
- remove unused plugin\_log and plugin\_configuration tables [\#553](https://github.com/alfio-event/alf.io/issues/553)
- remove unused/useless profiles [\#544](https://github.com/alfio-event/alf.io/issues/544)
- Reservation detail enhancements [\#541](https://github.com/alfio-event/alf.io/issues/541)
- Make easier to print again the badge of an attendee who already checked in [\#540](https://github.com/alfio-event/alf.io/issues/540)
- Integrate the new payment api [\#475](https://github.com/alfio-event/alf.io/issues/475)
- explore the use of the row security policy of pgsql [\#469](https://github.com/alfio-event/alf.io/issues/469)
- Explore how to remove quartz scheduler [\#361](https://github.com/alfio-event/alf.io/issues/361)
- Refactoring of Payment methods [\#323](https://github.com/alfio-event/alf.io/issues/323)

**Fixed bugs:**

- "copy from contact data" does not work for several tickets [\#517](https://github.com/alfio-event/alf.io/issues/517)
- Additional options are not shown anywere [\#516](https://github.com/alfio-event/alf.io/issues/516)

**Closed issues:**

- in M1/master: we don't send the ticket email automatically [\#585](https://github.com/alfio-event/alf.io/issues/585)
- instead of creating a new role application\_user -\> use  FORCE ROW LEVEL SECURITY [\#574](https://github.com/alfio-event/alf.io/issues/574)
- Update source level compatibility to JDK 11 [\#570](https://github.com/alfio-event/alf.io/issues/570)
- Remove old code that handle multi db [\#546](https://github.com/alfio-event/alf.io/issues/546)
- Store external systems IDs in Billing Document [\#543](https://github.com/alfio-event/alf.io/issues/543)
- Romanian translation [\#506](https://github.com/alfio-event/alf.io/issues/506)
- Import the "billing document" feature [\#476](https://github.com/alfio-event/alf.io/issues/476)

**Merged pull requests:**

- travis: test multiple postgresql versions [\#592](https://github.com/alfio-event/alf.io/pull/592) ([asymmetric](https://github.com/asymmetric))
- \#585 fix send tickets automatically [\#590](https://github.com/alfio-event/alf.io/pull/590) ([cbellone](https://github.com/cbellone))
- Docker: remove dependency on links feature [\#589](https://github.com/alfio-event/alf.io/pull/589) ([asymmetric](https://github.com/asymmetric))
- refactor: move code from manager to repository \(remove template queries\) [\#582](https://github.com/alfio-event/alf.io/pull/582) ([syjer](https://github.com/syjer))
- implement \#541 add reservation emails log in reservation [\#586](https://github.com/alfio-event/alf.io/pull/586) ([syjer](https://github.com/syjer))
- \#469 add row security policy [\#567](https://github.com/alfio-event/alf.io/pull/567) ([syjer](https://github.com/syjer))
- \#553 remove unused table plugin\_log and plugin\_configuration [\#555](https://github.com/alfio-event/alf.io/pull/555) ([syjer](https://github.com/syjer))
- implement \#361 quartz scheduler removal [\#552](https://github.com/alfio-event/alf.io/pull/552) ([syjer](https://github.com/syjer))
- implement \#544 refactor profiles [\#548](https://github.com/alfio-event/alf.io/pull/548) ([syjer](https://github.com/syjer))
- fix \#546 remove unused multi db code [\#547](https://github.com/alfio-event/alf.io/pull/547) ([syjer](https://github.com/syjer))
- Update spring 5.1 [\#545](https://github.com/alfio-event/alf.io/pull/545) ([syjer](https://github.com/syjer))

## [2.0-M0-EINVOICE-2](https://github.com/alfio-event/alf.io/tree/2.0-M0-EINVOICE-2) (2019-01-17)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.16.3...2.0-M0-EINVOICE-2)

## [1.16.3](https://github.com/alfio-event/alf.io/tree/1.16.3) (2019-01-16)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/2.0-M0-EINVOICE-1...1.16.3)

## [2.0-M0-EINVOICE-1](https://github.com/alfio-event/alf.io/tree/2.0-M0-EINVOICE-1) (2019-01-15)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.16.2...2.0-M0-EINVOICE-1)

## [1.16.2](https://github.com/alfio-event/alf.io/tree/1.16.2) (2019-01-15)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/2.0-M0-EINVOICE...1.16.2)

**Fixed bugs:**

- VAT rounding can be wrong in some cases [\#576](https://github.com/alfio-event/alf.io/issues/576)

**Merged pull requests:**

- partially implement \#579 reduce amount of data loaded [\#580](https://github.com/alfio-event/alf.io/pull/580) ([syjer](https://github.com/syjer))
- Use nested transaction where possible [\#575](https://github.com/alfio-event/alf.io/pull/575) ([syjer](https://github.com/syjer))

## [2.0-M0-EINVOICE](https://github.com/alfio-event/alf.io/tree/2.0-M0-EINVOICE) (2019-01-04)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/2.0-M0...2.0-M0-EINVOICE)

**Implemented enhancements:**

- Flyway config should take into account if the DB has already some tables [\#558](https://github.com/alfio-event/alf.io/issues/558)

**Fixed bugs:**

- Unused invoice number after failed online payment attempt [\#571](https://github.com/alfio-event/alf.io/issues/571)
- Mandatory fields do not let you buy a ticket [\#561](https://github.com/alfio-event/alf.io/issues/561)
- Unexpected change email owner notification on ticket update [\#287](https://github.com/alfio-event/alf.io/issues/287)

**Closed issues:**

- Update Bootstrap version  [\#562](https://github.com/alfio-event/alf.io/issues/562)
- Embedded Postgresql refuses to start [\#557](https://github.com/alfio-event/alf.io/issues/557)
- Promo code should have optional "total usage" counter [\#441](https://github.com/alfio-event/alf.io/issues/441)
- Google API Key should be optional [\#127](https://github.com/alfio-event/alf.io/issues/127)

**Merged pull requests:**

- Migrate source code to JDK11 [\#572](https://github.com/alfio-event/alf.io/pull/572) ([cbellone](https://github.com/cbellone))
- \#476 import billing document [\#563](https://github.com/alfio-event/alf.io/pull/563) ([cbellone](https://github.com/cbellone))
- Detect if Flyway should avoid to check if the actual schema is non-empty [\#559](https://github.com/alfio-event/alf.io/pull/559) ([cougil](https://github.com/cougil))
-  \#540 Add a button to display QR-CODE in checked-in tab [\#550](https://github.com/alfio-event/alf.io/pull/550) ([NassimBounouas](https://github.com/NassimBounouas))
- Merge payment methods [\#501](https://github.com/alfio-event/alf.io/pull/501) ([cbellone](https://github.com/cbellone))

## [2.0-M0](https://github.com/alfio-event/alf.io/tree/2.0-M0) (2018-11-01)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.16.1...2.0-M0)

**Implemented enhancements:**

- Attendee form: add additional fields before standard fields [\#539](https://github.com/alfio-event/alf.io/issues/539)
- import new reservation flow [\#464](https://github.com/alfio-event/alf.io/issues/464)

**Closed issues:**

- move all views in afterMigrate\_\_\* scripts [\#479](https://github.com/alfio-event/alf.io/issues/479)
- Translations for 2.0-M0 [\#477](https://github.com/alfio-event/alf.io/issues/477)
- Switch vatchecker implementation [\#468](https://github.com/alfio-event/alf.io/issues/468)
- Update to spring boot 2 and spring 5 [\#467](https://github.com/alfio-event/alf.io/issues/467)
- Remove hsqldb and mysql support [\#466](https://github.com/alfio-event/alf.io/issues/466)

**Merged pull requests:**

- Switch to spring5 and spring boot2, implement \#467 [\#473](https://github.com/alfio-event/alf.io/pull/473) ([syjer](https://github.com/syjer))
- Implement \#466 Remove hsql/mysql [\#471](https://github.com/alfio-event/alf.io/pull/471) ([syjer](https://github.com/syjer))
- implement \#468 switch euvatchecker v2 [\#470](https://github.com/alfio-event/alf.io/pull/470) ([syjer](https://github.com/syjer))

## [1.16.1](https://github.com/alfio-event/alf.io/tree/1.16.1) (2018-11-01)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.16...1.16.1)

**Implemented enhancements:**

- Resize image on upload [\#527](https://github.com/alfio-event/alf.io/issues/527)
- Display a warning message when alf.io is in production mode and not used over https [\#515](https://github.com/alfio-event/alf.io/issues/515)
- Add check-in statistics in the mobile app [\#426](https://github.com/alfio-event/alf.io/issues/426)

**Fixed bugs:**

- Cannot update organization on an existing event [\#535](https://github.com/alfio-event/alf.io/issues/535)

**Closed issues:**

- STARTTLS for SMTP [\#536](https://github.com/alfio-event/alf.io/issues/536)
- Configure first and last name on the label [\#533](https://github.com/alfio-event/alf.io/issues/533)
- When applying a category specific discount code but \_not\_ selecting the discounted ticket, the discount code with an amount of 0 will appear in the summary [\#532](https://github.com/alfio-event/alf.io/issues/532)
- Remove "http" \(Spring\) profile, because it does not seems to work \(and is confusing\) [\#514](https://github.com/alfio-event/alf.io/issues/514)
- How to launch in production/non-dev mode with only 8080 and no HTTPs ? [\#511](https://github.com/alfio-event/alf.io/issues/511)
- Docker Compose Support [\#363](https://github.com/alfio-event/alf.io/issues/363)

**Merged pull requests:**

- Implement \#527 resize image on upload [\#538](https://github.com/alfio-event/alf.io/pull/538) ([syjer](https://github.com/syjer))
- Add Docker Compose support [\#534](https://github.com/alfio-event/alf.io/pull/534) ([asymmetric](https://github.com/asymmetric))
- Fixed French errors [\#526](https://github.com/alfio-event/alf.io/pull/526) ([theute](https://github.com/theute))
- remove anyway broken "http" \(Spring Boot\) profile \(fixes \#514\) [\#525](https://github.com/alfio-event/alf.io/pull/525) ([vorburger](https://github.com/vorburger))
- implement \#515, show message if alf.io is used in dev mode or in prod mode not over https, for 1.x [\#519](https://github.com/alfio-event/alf.io/pull/519) ([syjer](https://github.com/syjer))
- implement \#515, show message if alf.io is used in dev mode or in prod mode not over https [\#518](https://github.com/alfio-event/alf.io/pull/518) ([syjer](https://github.com/syjer))

## [1.16](https://github.com/alfio-event/alf.io/tree/1.16) (2018-09-28)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.16-RC2...1.16)

**Implemented enhancements:**

- Bulk check-in [\#508](https://github.com/alfio-event/alf.io/issues/508)
- Display additional options at check-in [\#512](https://github.com/alfio-event/alf.io/issues/512)
- Allow additional rows to be printed in the label [\#498](https://github.com/alfio-event/alf.io/issues/498)
- Excel \(xslx\) export [\#488](https://github.com/alfio-event/alf.io/issues/488)

**Fixed bugs:**

- Cannot change a user details, without also changing the username. [\#503](https://github.com/alfio-event/alf.io/issues/503)

**Closed issues:**

- Display Transaction details in the attendees export [\#507](https://github.com/alfio-event/alf.io/issues/507)
- Add template edit at the organization level [\#499](https://github.com/alfio-event/alf.io/issues/499)
- Attendee Whitelist [\#487](https://github.com/alfio-event/alf.io/issues/487)
- Api key support [\#486](https://github.com/alfio-event/alf.io/issues/486)

**Merged pull requests:**

- implement \#508 bulk check-in [\#510](https://github.com/alfio-event/alf.io/pull/510) ([syjer](https://github.com/syjer))
- limit discount code usage: pick + fixes [\#502](https://github.com/alfio-event/alf.io/pull/502) ([syjer](https://github.com/syjer))

## [1.16-RC2](https://github.com/alfio-event/alf.io/tree/1.16-RC2) (2018-09-18)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.16-RC1...1.16-RC2)

**Closed issues:**

- Could not work sending mail via mailgun. [\#504](https://github.com/alfio-event/alf.io/issues/504)

## [1.16-RC1](https://github.com/alfio-event/alf.io/tree/1.16-RC1) (2018-08-30)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.15...1.16-RC1)

**Implemented enhancements:**

- Mark the login qrcode for mobile app [\#382](https://github.com/alfio-event/alf.io/issues/382)

**Closed issues:**

- Docker for me not working [\#460](https://github.com/alfio-event/alf.io/issues/460)

**Merged pull requests:**

- \#499 add organization level template editing [\#500](https://github.com/alfio-event/alf.io/pull/500) ([syjer](https://github.com/syjer))
- Merge whitelist to master [\#496](https://github.com/alfio-event/alf.io/pull/496) ([cbellone](https://github.com/cbellone))
- 487 whitelist [\#491](https://github.com/alfio-event/alf.io/pull/491) ([cbellone](https://github.com/cbellone))
- implement: \#488 add excel support for export [\#490](https://github.com/alfio-event/alf.io/pull/490) ([syjer](https://github.com/syjer))
- \#486 api key support [\#489](https://github.com/alfio-event/alf.io/pull/489) ([syjer](https://github.com/syjer))

## [1.15](https://github.com/alfio-event/alf.io/tree/1.15) (2018-07-11)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.15-RC3...1.15)

**Implemented enhancements:**

- Improve category stats popover readability [\#485](https://github.com/alfio-event/alf.io/issues/485)
- Pdf rendering: try to reduce memory consumption: don't load the fonts in memory, use temp files [\#481](https://github.com/alfio-event/alf.io/issues/481)

## [1.15-RC3](https://github.com/alfio-event/alf.io/tree/1.15-RC3) (2018-07-03)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.15-RC2...1.15-RC3)

**Implemented enhancements:**

- Vat checker validation extension [\#483](https://github.com/alfio-event/alf.io/issues/483)
- Print additional information on ticket [\#482](https://github.com/alfio-event/alf.io/issues/482)

**Merged pull requests:**

- New reservation flow [\#484](https://github.com/alfio-event/alf.io/pull/484) ([syjer](https://github.com/syjer))

## [1.15-RC2](https://github.com/alfio-event/alf.io/tree/1.15-RC2) (2018-06-28)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.15-RC1...1.15-RC2)

## [1.15-RC1](https://github.com/alfio-event/alf.io/tree/1.15-RC1) (2018-06-23)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.14.2...1.15-RC1)

**Implemented enhancements:**

- Include PDF invoice in reservation email [\#459](https://github.com/alfio-event/alf.io/issues/459)
- Have the possibility to disable the contact details being added automatically to the ticket [\#457](https://github.com/alfio-event/alf.io/issues/457)
- Invoice enhancements [\#449](https://github.com/alfio-event/alf.io/issues/449)
- Add a possibility to always generate an invoice instead of the invoice/receipt dichotomy for private customers [\#443](https://github.com/alfio-event/alf.io/issues/443)
- Add support for Canadian Tax Code \(GST\) [\#431](https://github.com/alfio-event/alf.io/issues/431)

**Fixed bugs:**

- Existing Privacy Policy url is again empty when editing event [\#440](https://github.com/alfio-event/alf.io/issues/440)
- Follow EU VAT Rules \(for event in Belgium\) [\#432](https://github.com/alfio-event/alf.io/issues/432)
- Dashboard Gross income is not updating correctly [\#427](https://github.com/alfio-event/alf.io/issues/427)

**Closed issues:**

- email: handle cases where the emails are stuck "IN\_PROCESS" for more than 1h [\#478](https://github.com/alfio-event/alf.io/issues/478)
- Provide a Purchase Order textfield during registration [\#458](https://github.com/alfio-event/alf.io/issues/458)
- VAT country is not added to billing address [\#453](https://github.com/alfio-event/alf.io/issues/453)
- Reservations should not be deleted automatically when expired [\#447](https://github.com/alfio-event/alf.io/issues/447)
- Missing I18N entry 'reservation-page.expiration-month' for en\_GB  [\#438](https://github.com/alfio-event/alf.io/issues/438)
- The possibility to turn-off "I don't know yet who will attend" [\#436](https://github.com/alfio-event/alf.io/issues/436)
- Make it clear which VAT Country code is used [\#433](https://github.com/alfio-event/alf.io/issues/433)

**Merged pull requests:**

- Event API v1 [\#480](https://github.com/alfio-event/alf.io/pull/480) ([minettiandrea](https://github.com/minettiandrea))
- add additional dejavusans/serif fonts \(for invoice\) [\#474](https://github.com/alfio-event/alf.io/pull/474) ([syjer](https://github.com/syjer))
- implement \#443 invoice only generation [\#456](https://github.com/alfio-event/alf.io/pull/456) ([syjer](https://github.com/syjer))
- implement \#436 force ticket owner assignment at registration [\#454](https://github.com/alfio-event/alf.io/pull/454) ([syjer](https://github.com/syjer))
- implement: \#447, add the possibility to configure the auto-removal of expired offline-payment [\#452](https://github.com/alfio-event/alf.io/pull/452) ([syjer](https://github.com/syjer))
- \#449 invoice enhancements [\#450](https://github.com/alfio-event/alf.io/pull/450) ([cbellone](https://github.com/cbellone))
- Add reset billing info [\#448](https://github.com/alfio-event/alf.io/pull/448) ([syjer](https://github.com/syjer))
- implement \#433  [\#442](https://github.com/alfio-event/alf.io/pull/442) ([syjer](https://github.com/syjer))
- implement \#431  [\#437](https://github.com/alfio-event/alf.io/pull/437) ([syjer](https://github.com/syjer))

## [1.14.2](https://github.com/alfio-event/alf.io/tree/1.14.2) (2018-05-14)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.14.1...1.14.2)

**Fixed bugs:**

- Slash in organization name makes requests fail [\#424](https://github.com/alfio-event/alf.io/issues/424)
- Reservation list is not ordered [\#423](https://github.com/alfio-event/alf.io/issues/423)

## [1.14.1](https://github.com/alfio-event/alf.io/tree/1.14.1) (2018-05-07)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.14...1.14.1)

**Implemented enhancements:**

- Improve docker support [\#422](https://github.com/alfio-event/alf.io/issues/422)
- Add more information in expiring payments notification email [\#420](https://github.com/alfio-event/alf.io/issues/420)
- Introduce GDPR Privacy Policy link [\#419](https://github.com/alfio-event/alf.io/issues/419)
- His Language if it only has one option [\#417](https://github.com/alfio-event/alf.io/issues/417)
- Search by attendee in reservation list [\#414](https://github.com/alfio-event/alf.io/issues/414)
- add TicketReservation object to extension scope on RESERVATION\_CANCELLED [\#412](https://github.com/alfio-event/alf.io/issues/412)

**Fixed bugs:**

- Cannot create custom reservation with 1 empty seat [\#421](https://github.com/alfio-event/alf.io/issues/421)
- Error while creating a new event [\#416](https://github.com/alfio-event/alf.io/issues/416)
- RESERVATION\_CANCELLED not fired when deleting a pending payment [\#413](https://github.com/alfio-event/alf.io/issues/413)

**Closed issues:**

- VAT Checker not working [\#418](https://github.com/alfio-event/alf.io/issues/418)

## [1.14](https://github.com/alfio-event/alf.io/tree/1.14) (2018-04-10)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.14-RC2...1.14)

**Implemented enhancements:**

- add support for here map [\#366](https://github.com/alfio-event/alf.io/issues/366)
- add internal hook points support  with scripting capabilities [\#349](https://github.com/alfio-event/alf.io/issues/349)

**Fixed bugs:**

- Cannot set custom template if the same template name exists for another event [\#411](https://github.com/alfio-event/alf.io/issues/411)
- Remove Ticket doesn't work for "pending payment" reservations [\#407](https://github.com/alfio-event/alf.io/issues/407)
- Invoice with VAT included VS additional option [\#405](https://github.com/alfio-event/alf.io/issues/405)
- No invoice for Paypal payments? [\#404](https://github.com/alfio-event/alf.io/issues/404)
- Stripe payments not working - Mandatory configuration key STRIPE\_CONNECTED\_ID not present [\#392](https://github.com/alfio-event/alf.io/issues/392)

**Closed issues:**

- Removing attendees from a reservation doesn't update the invoice [\#410](https://github.com/alfio-event/alf.io/issues/410)
- Add support for Clever Cloud [\#385](https://github.com/alfio-event/alf.io/issues/385)

**Merged pull requests:**

- \#323 Refactoring of Payment methods - TODO fix tests [\#408](https://github.com/alfio-event/alf.io/pull/408) ([mariofusco](https://github.com/mariofusco))
- implement \#366, add here maps support [\#367](https://github.com/alfio-event/alf.io/pull/367) ([syjer](https://github.com/syjer))

## [1.14-RC2](https://github.com/alfio-event/alf.io/tree/1.14-RC2) (2018-03-28)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.14-RC1...1.14-RC2)

## [1.14-RC1](https://github.com/alfio-event/alf.io/tree/1.14-RC1) (2018-03-19)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.13.3...1.14-RC1)

**Fixed bugs:**

- Paypal payments not working - INVALID\_EXPERIENCE\_PROFILE\_ID [\#393](https://github.com/alfio-event/alf.io/issues/393)

**Merged pull requests:**

- Dutch language update [\#406](https://github.com/alfio-event/alf.io/pull/406) ([mg-1999](https://github.com/mg-1999))
- fix README bad mark-up for "Running with multiple profiles" [\#402](https://github.com/alfio-event/alf.io/pull/402) ([vorburger](https://github.com/vorburger))

## [1.13.3](https://github.com/alfio-event/alf.io/tree/1.13.3) (2018-02-15)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.13.2...1.13.3)

**Fixed bugs:**

- VAT API address [\#388](https://github.com/alfio-event/alf.io/issues/388)
- Missing paid / refunded amount on reservation detail [\#384](https://github.com/alfio-event/alf.io/issues/384)
- Error trying to request the receipt or the invoice [\#383](https://github.com/alfio-event/alf.io/issues/383)

**Merged pull requests:**

- Fix the wrong URL attribute of an Event [\#389](https://github.com/alfio-event/alf.io/pull/389) ([kivanov82](https://github.com/kivanov82))

## [1.13.2](https://github.com/alfio-event/alf.io/tree/1.13.2) (2018-01-31)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.13.1...1.13.2)

**Fixed bugs:**

- Reservations list: cannot search by uuid [\#377](https://github.com/alfio-event/alf.io/issues/377)
- wrong responsive layout on tablet [\#376](https://github.com/alfio-event/alf.io/issues/376)
- email sending stop working [\#373](https://github.com/alfio-event/alf.io/issues/373)
- Internal Error on admin profile [\#370](https://github.com/alfio-event/alf.io/issues/370)
- Cannot assign tickets to Dynamic categories [\#185](https://github.com/alfio-event/alf.io/issues/185)

**Closed issues:**

- Build errors [\#375](https://github.com/alfio-event/alf.io/issues/375)
- 403 on staticmap [\#372](https://github.com/alfio-event/alf.io/issues/372)
- New event failure: API has changed? [\#356](https://github.com/alfio-event/alf.io/issues/356)

**Merged pull requests:**

- merge \#349 scripting hooks [\#374](https://github.com/alfio-event/alf.io/pull/374) ([syjer](https://github.com/syjer))

## [1.13.1](https://github.com/alfio-event/alf.io/tree/1.13.1) (2017-12-11)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.13...1.13.1)

**Fixed bugs:**

- Update category price on a dynamic category resets prices of existing \(confirmed\) tickets [\#369](https://github.com/alfio-event/alf.io/issues/369)

## [1.13](https://github.com/alfio-event/alf.io/tree/1.13) (2017-11-23)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.13-RC3...1.13)

**Implemented enhancements:**

- Hide sensitive information when the user is a "check-in supervisor" [\#359](https://github.com/alfio-event/alf.io/issues/359)
- Show ticket validity date instead of event date [\#357](https://github.com/alfio-event/alf.io/issues/357)
- Waiting queue form should be displayed if all "public" tickets have been sold [\#348](https://github.com/alfio-event/alf.io/issues/348)
- save used vat% in each ticket [\#347](https://github.com/alfio-event/alf.io/issues/347)
- Add possibility to disable email sending when mode is PROFILE\_LIVE [\#346](https://github.com/alfio-event/alf.io/issues/346)

**Fixed bugs:**

- Reset UUID when ticket is released by the attendee or removed by the admin [\#365](https://github.com/alfio-event/alf.io/issues/365)
- Reservation list: filter should be case-insensitive [\#360](https://github.com/alfio-event/alf.io/issues/360)
- Search from Check-in view has 2 separate fields, but only 1 is used [\#342](https://github.com/alfio-event/alf.io/issues/342)
- Consider invitations when invalidating tokens [\#335](https://github.com/alfio-event/alf.io/issues/335)

**Closed issues:**

- multi-tenancy: add support for platform mode [\#343](https://github.com/alfio-event/alf.io/issues/343)
- Set Checkin-date on category [\#333](https://github.com/alfio-event/alf.io/issues/333)

**Merged pull requests:**

- add support for french [\#350](https://github.com/alfio-event/alf.io/pull/350) ([bjamet](https://github.com/bjamet))

## [1.13-RC3](https://github.com/alfio-event/alf.io/tree/1.13-RC3) (2017-10-31)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.13-RC2...1.13-RC3)

## [1.13-RC2](https://github.com/alfio-event/alf.io/tree/1.13-RC2) (2017-10-28)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.13-RC1...1.13-RC2)

**Closed issues:**

- waiting list: revert access restricted tickets to free [\#355](https://github.com/alfio-event/alf.io/issues/355)
- Upload existing attendees [\#354](https://github.com/alfio-event/alf.io/issues/354)

## [1.13-RC1](https://github.com/alfio-event/alf.io/tree/1.13-RC1) (2017-10-24)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.12.1...1.13-RC1)

## [1.12.1](https://github.com/alfio-event/alf.io/tree/1.12.1) (2017-10-08)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.12...1.12.1)

**Merged pull requests:**

- \#343 - configure "platform mode" in order to collect fees for each ti… [\#344](https://github.com/alfio-event/alf.io/pull/344) ([cbellone](https://github.com/cbellone))
- implement \#333 checkin date category [\#341](https://github.com/alfio-event/alf.io/pull/341) ([syjer](https://github.com/syjer))

## [1.12](https://github.com/alfio-event/alf.io/tree/1.12) (2017-09-20)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.12-RC4...1.12)

**Implemented enhancements:**

- misc optimizations [\#325](https://github.com/alfio-event/alf.io/issues/325)
- remove UserApiController.storePasswordImage, switch to a post+base64img -\> data url [\#324](https://github.com/alfio-event/alf.io/issues/324)
- offline check-in data: support delta requests + optional data [\#319](https://github.com/alfio-event/alf.io/issues/319)
- Lazy loading of tickets [\#318](https://github.com/alfio-event/alf.io/issues/318)
- add spring-session with jdbc backend [\#312](https://github.com/alfio-event/alf.io/issues/312)
- edit fields: should be able to add additional elements to select and switch from mandatory to optional [\#306](https://github.com/alfio-event/alf.io/issues/306)
- improve email log UI/UX [\#288](https://github.com/alfio-event/alf.io/issues/288)
- Suspend waiting queue [\#201](https://github.com/alfio-event/alf.io/issues/201)

**Fixed bugs:**

- error while checking for pending payments [\#332](https://github.com/alfio-event/alf.io/issues/332)
- Serialization issue with profile jdbc-session [\#331](https://github.com/alfio-event/alf.io/issues/331)
- Add feedback on save at organization / event level [\#317](https://github.com/alfio-event/alf.io/issues/317)
- Can't configure EU-Billing country at organization level [\#316](https://github.com/alfio-event/alf.io/issues/316)

**Closed issues:**

- fix getOfflineIdentifiers to handle the case a ticket does not have a last\_update time [\#339](https://github.com/alfio-event/alf.io/issues/339)
- Add captcha if offline payment has been selected [\#338](https://github.com/alfio-event/alf.io/issues/338)
- select box values are not saved with webkit [\#307](https://github.com/alfio-event/alf.io/issues/307)
- google maps geo api: switch to client side only \(remove server side timezone api\) [\#304](https://github.com/alfio-event/alf.io/issues/304)
- handle specific paypal error codes [\#303](https://github.com/alfio-event/alf.io/issues/303)
- Demo mode [\#299](https://github.com/alfio-event/alf.io/issues/299)
- Feature Request: Show which tokens have already been sent out [\#290](https://github.com/alfio-event/alf.io/issues/290)

**Merged pull requests:**

- use the correct geoapi, fix \#314 [\#315](https://github.com/alfio-event/alf.io/pull/315) ([syjer](https://github.com/syjer))

## [1.12-RC4](https://github.com/alfio-event/alf.io/tree/1.12-RC4) (2017-09-12)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.12-RC3...1.12-RC4)

**Merged pull requests:**

- 288 email log [\#336](https://github.com/alfio-event/alf.io/pull/336) ([syjer](https://github.com/syjer))

## [1.12-RC3](https://github.com/alfio-event/alf.io/tree/1.12-RC3) (2017-09-08)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.12-RC2...1.12-RC3)

## [1.12-RC2](https://github.com/alfio-event/alf.io/tree/1.12-RC2) (2017-09-05)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.12-RC1...1.12-RC2)

## [1.12-RC1](https://github.com/alfio-event/alf.io/tree/1.12-RC1) (2017-09-04)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.11.1...1.12-RC1)

**Implemented enhancements:**

- fast code url  + code for category [\#328](https://github.com/alfio-event/alf.io/issues/328)

**Fixed bugs:**

- increase category size could lead to incoherency [\#330](https://github.com/alfio-event/alf.io/issues/330)
- Cannot download Event data CSV [\#320](https://github.com/alfio-event/alf.io/issues/320)
- Proper error screen when "Cannot confirm an offline reservation after event start" [\#197](https://github.com/alfio-event/alf.io/issues/197)

**Closed issues:**

- Empty zip when there are no invoices [\#321](https://github.com/alfio-event/alf.io/issues/321)
- geoapi take2 [\#314](https://github.com/alfio-event/alf.io/issues/314)
- Japanese characters in iOS App are garbled characters [\#302](https://github.com/alfio-event/alf.io/issues/302)
- stateless csrf [\#279](https://github.com/alfio-event/alf.io/issues/279)
- add mysql 5.7 in the travis test matrix [\#140](https://github.com/alfio-event/alf.io/issues/140)

**Merged pull requests:**

- implement \#328 event code url [\#329](https://github.com/alfio-event/alf.io/pull/329) ([syjer](https://github.com/syjer))
- \#318 remove last single event with statistics: WIP [\#326](https://github.com/alfio-event/alf.io/pull/326) ([syjer](https://github.com/syjer))
- add mysql 5.7 in travis test matrix [\#313](https://github.com/alfio-event/alf.io/pull/313) ([syjer](https://github.com/syjer))

## [1.11.1](https://github.com/alfio-event/alf.io/tree/1.11.1) (2017-08-01)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.11...1.11.1)

**Fixed bugs:**

- Update event: additional tickets should have "RELEASED" status instead of "FREE" [\#309](https://github.com/alfio-event/alf.io/issues/309)
- Ticket cancellation: wrong status [\#300](https://github.com/alfio-event/alf.io/issues/300)
- Error while loading users if there are no results [\#298](https://github.com/alfio-event/alf.io/issues/298)
- Cannot save custom PDF resource [\#297](https://github.com/alfio-event/alf.io/issues/297)

**Closed issues:**

- xhr handling issue when handling js geolocation task [\#250](https://github.com/alfio-event/alf.io/issues/250)
- UX: no feedback provided when clicking save button in config section [\#247](https://github.com/alfio-event/alf.io/issues/247)

**Merged pull requests:**

- 304 - geoapi switch [\#310](https://github.com/alfio-event/alf.io/pull/310) ([syjer](https://github.com/syjer))

## [1.11](https://github.com/alfio-event/alf.io/tree/1.11) (2017-06-01)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.10.2...1.11)

**Implemented enhancements:**

- Support partial refund [\#280](https://github.com/alfio-event/alf.io/issues/280)
- Promo code at organization level [\#291](https://github.com/alfio-event/alf.io/issues/291)
- Improve UI/UX of admin [\#282](https://github.com/alfio-event/alf.io/issues/282)
- VAT management for EU [\#278](https://github.com/alfio-event/alf.io/issues/278)
- notify organizer of expiring pending payments [\#277](https://github.com/alfio-event/alf.io/issues/277)
- Display pending reservations in the event detail [\#244](https://github.com/alfio-event/alf.io/issues/244)
- Support passbook when sending ticket [\#214](https://github.com/alfio-event/alf.io/issues/214)
- support refund [\#208](https://github.com/alfio-event/alf.io/issues/208)
- Add a remove ticket function [\#114](https://github.com/alfio-event/alf.io/issues/114)
- add more caching for uploaded file [\#58](https://github.com/alfio-event/alf.io/issues/58)

**Fixed bugs:**

- Billing Address remains also if inserting a VAT number [\#295](https://github.com/alfio-event/alf.io/issues/295)
- Remove ticket confirmation panel shows firstname instead of lastname [\#294](https://github.com/alfio-event/alf.io/issues/294)
- I need an invoice not working for "Outside EU" [\#293](https://github.com/alfio-event/alf.io/issues/293)
- Additional option are calculated wrong [\#292](https://github.com/alfio-event/alf.io/issues/292)

**Closed issues:**

- Backend Android App Unexpected Error [\#283](https://github.com/alfio-event/alf.io/issues/283)
- better user handling [\#289](https://github.com/alfio-event/alf.io/issues/289)
- send reservation notification to multiple addresses [\#285](https://github.com/alfio-event/alf.io/issues/285)
- Update contact data/billing address [\#275](https://github.com/alfio-event/alf.io/issues/275)
- Implement auditing [\#135](https://github.com/alfio-event/alf.io/issues/135)
- Support additional items [\#111](https://github.com/alfio-event/alf.io/issues/111)

**Merged pull requests:**

- Dutch lang update [\#296](https://github.com/alfio-event/alf.io/pull/296) ([mg-1999](https://github.com/mg-1999))
- \#247 feedback message added for configuration save action  [\#272](https://github.com/alfio-event/alf.io/pull/272) ([Praitheesh](https://github.com/Praitheesh))

## [1.10.2](https://github.com/alfio-event/alf.io/tree/1.10.2) (2017-04-10)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.10.1...1.10.2)

**Fixed bugs:**

- Cannot confirm a reservation after updating contact data from admin [\#271](https://github.com/alfio-event/alf.io/issues/271)
- does not generate the invoice when using the combination: admin: create reservation + offline payment [\#270](https://github.com/alfio-event/alf.io/issues/270)

**Closed issues:**

- Updating to latest release [\#273](https://github.com/alfio-event/alf.io/issues/273)
- MediaStreamTrack.getSources\(\) is deprecated and removed from Chrome [\#265](https://github.com/alfio-event/alf.io/issues/265)
-  Alf.io-1.10 - Error running in Eclipse [\#263](https://github.com/alfio-event/alf.io/issues/263)

**Merged pull requests:**

- \#197 if event began already and has only offline payment method then … [\#262](https://github.com/alfio-event/alf.io/pull/262) ([Praitheesh](https://github.com/Praitheesh))

## [1.10.1](https://github.com/alfio-event/alf.io/tree/1.10.1) (2017-03-13)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.10...1.10.1)

**Fixed bugs:**

- Additional fields values not deleted when the reservation expires [\#264](https://github.com/alfio-event/alf.io/issues/264)

## [1.10](https://github.com/alfio-event/alf.io/tree/1.10) (2017-02-19)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.10-RC2...1.10)

**Implemented enhancements:**

- simplified reservation creation at check in [\#261](https://github.com/alfio-event/alf.io/issues/261)
- Send Invoice for pending reservations [\#219](https://github.com/alfio-event/alf.io/issues/219)

**Fixed bugs:**

- csv create reservation has an encoding issue [\#260](https://github.com/alfio-event/alf.io/issues/260)

**Closed issues:**

- Hide expired events after 2 weeks from end date [\#233](https://github.com/alfio-event/alf.io/issues/233)
- For unpublished events, add a yellow icon before event name in the dashboard [\#232](https://github.com/alfio-event/alf.io/issues/232)
- Show "Confirmation Date" in the attendee's data CSV report [\#207](https://github.com/alfio-event/alf.io/issues/207)
- Additional fields: optional/mandatory [\#153](https://github.com/alfio-event/alf.io/issues/153)
- Print badges during check-in [\#134](https://github.com/alfio-event/alf.io/issues/134)
- Offline-mode check-in [\#133](https://github.com/alfio-event/alf.io/issues/133)

## [1.10-RC2](https://github.com/alfio-event/alf.io/tree/1.10-RC2) (2017-02-08)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.10-RC1...1.10-RC2)

## [1.10-RC1](https://github.com/alfio-event/alf.io/tree/1.10-RC1) (2017-02-08)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.9.3...1.10-RC1)

**Closed issues:**

- Support for Payment Gateway Library - Omnipay \[Feature/Enhancement Request\]  [\#257](https://github.com/alfio-event/alf.io/issues/257)

**Merged pull requests:**

- \#175 duplicate additional-field.name validation added  [\#256](https://github.com/alfio-event/alf.io/pull/256) ([Praitheesh](https://github.com/Praitheesh))
- \#207 add Confirmation date column into export csv.  [\#255](https://github.com/alfio-event/alf.io/pull/255) ([Praitheesh](https://github.com/Praitheesh))
- \#153 optional/mandatory validation added for additional fields [\#254](https://github.com/alfio-event/alf.io/pull/254) ([Praitheesh](https://github.com/Praitheesh))

## [1.9.3](https://github.com/alfio-event/alf.io/tree/1.9.3) (2017-01-05)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.9.2...1.9.3)

**Fixed bugs:**

- Offline payment: assignee data is not saved [\#251](https://github.com/alfio-event/alf.io/issues/251)

**Closed issues:**

- make "Add to my calendar" link open in new tab. [\#248](https://github.com/alfio-event/alf.io/issues/248)

## [1.9.2](https://github.com/alfio-event/alf.io/tree/1.9.2) (2016-11-14)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.9.1...1.9.2)

**Fixed bugs:**

- Admin reservations are wrongly notified [\#246](https://github.com/alfio-event/alf.io/issues/246)

## [1.9.1](https://github.com/alfio-event/alf.io/tree/1.9.1) (2016-11-07)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.9...1.9.1)

## [1.9](https://github.com/alfio-event/alf.io/tree/1.9) (2016-11-07)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.8.2...1.9)

**Implemented enhancements:**

- Allow event organizer to postpone the expiration date for pending tickets [\#218](https://github.com/alfio-event/alf.io/issues/218)
- Enable jetty WorkerName [\#242](https://github.com/alfio-event/alf.io/issues/242)
- Add link to GitHub Issues in footer [\#227](https://github.com/alfio-event/alf.io/issues/227)
- Possibility to remove people/tickets in a waiting queue  [\#226](https://github.com/alfio-event/alf.io/issues/226)
- Un-publish events [\#225](https://github.com/alfio-event/alf.io/issues/225)
- Email templates should be user editable [\#213](https://github.com/alfio-event/alf.io/issues/213)
- Ask for attendee's data and additional fields in the checkout form [\#212](https://github.com/alfio-event/alf.io/issues/212)
- Ability to unmark a ticket as checked-in [\#195](https://github.com/alfio-event/alf.io/issues/195)

**Fixed bugs:**

- optimize //admin/api/events.json call [\#237](https://github.com/alfio-event/alf.io/issues/237)
- Max tickets defined at category level not working [\#229](https://github.com/alfio-event/alf.io/issues/229)

**Closed issues:**

- Create Reservation: clarify price is per ticket [\#245](https://github.com/alfio-event/alf.io/issues/245)
- Validation error message when assigning tickets [\#238](https://github.com/alfio-event/alf.io/issues/238)
- Add tickets/reservations from admin [\#234](https://github.com/alfio-event/alf.io/issues/234)
- Add an option to customize the ticket template [\#88](https://github.com/alfio-event/alf.io/issues/88)

**Merged pull requests:**

- Update language [\#236](https://github.com/alfio-event/alf.io/pull/236) ([mg-1999](https://github.com/mg-1999))

## [1.8.2](https://github.com/alfio-event/alf.io/tree/1.8.2) (2016-10-18)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.8.1...1.8.2)

**Closed issues:**

- Event time zone  issue [\#228](https://github.com/alfio-event/alf.io/issues/228)
- Add a button to unpublish an event [\#224](https://github.com/alfio-event/alf.io/issues/224)

## [1.8.1](https://github.com/alfio-event/alf.io/tree/1.8.1) (2016-09-27)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.8...1.8.1)

**Implemented enhancements:**

- Organizations may define an Admin user [\#222](https://github.com/alfio-event/alf.io/issues/222)
- Optional field subordinate to donation options [\#215](https://github.com/alfio-event/alf.io/issues/215)
- Allow multiple option purchase [\#211](https://github.com/alfio-event/alf.io/issues/211)
- improve bulk email handling [\#203](https://github.com/alfio-event/alf.io/issues/203)

**Fixed bugs:**

- Wrong default value for category end date [\#179](https://github.com/alfio-event/alf.io/issues/179)
- Dashboard view should use the blank space on the left of the window [\#221](https://github.com/alfio-event/alf.io/issues/221)
- Allow title and description modification in Edit Donation Option [\#210](https://github.com/alfio-event/alf.io/issues/210)

**Closed issues:**

- Check-in operators shouldn't access the admin GUI [\#216](https://github.com/alfio-event/alf.io/issues/216)
- ERR\_TOO\_MANY\_REDIRECTS [\#206](https://github.com/alfio-event/alf.io/issues/206)
- Cannot create new event, error on form?? [\#205](https://github.com/alfio-event/alf.io/issues/205)
- admin: delete config doesn't work [\#204](https://github.com/alfio-event/alf.io/issues/204)
- additional email handling work [\#99](https://github.com/alfio-event/alf.io/issues/99)

## [1.8](https://github.com/alfio-event/alf.io/tree/1.8) (2016-09-12)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.8-RC2...1.8)

**Implemented enhancements:**

- create event as draft [\#202](https://github.com/alfio-event/alf.io/issues/202)
- add ticket PDF when sending message to attendees [\#200](https://github.com/alfio-event/alf.io/issues/200)
- The manual "Check-In" button only appears when there's a single match [\#196](https://github.com/alfio-event/alf.io/issues/196)
- Create new event: persist draft during editing [\#184](https://github.com/alfio-event/alf.io/issues/184)
- Create new event: Ticket form fields, new field [\#181](https://github.com/alfio-event/alf.io/issues/181)
- Change Password: add warning if admin user [\#180](https://github.com/alfio-event/alf.io/issues/180)
- Add link to Markdown reference close to the "Preview" button [\#176](https://github.com/alfio-event/alf.io/issues/176)
- Mailchimp - add event key as user attribute [\#171](https://github.com/alfio-event/alf.io/issues/171)
- Sold tickets: show number in the dashboard [\#154](https://github.com/alfio-event/alf.io/issues/154)
- Additional fields: they're hard to see if I buy only one ticket [\#152](https://github.com/alfio-event/alf.io/issues/152)
- Improve VAT/Price management [\#148](https://github.com/alfio-event/alf.io/issues/148)
- Logout and Ability to Change Password [\#117](https://github.com/alfio-event/alf.io/issues/117)
- Assign a discount code to a specific category [\#112](https://github.com/alfio-event/alf.io/issues/112)
- Donate an arbitrary amount to the event [\#110](https://github.com/alfio-event/alf.io/issues/110)
- Update spring-boot to 1.3 [\#106](https://github.com/alfio-event/alf.io/issues/106)
- Add possibility to update validity date for promo code [\#103](https://github.com/alfio-event/alf.io/issues/103)
- Split full name in first/last name fields [\#102](https://github.com/alfio-event/alf.io/issues/102)
- Remove Event [\#100](https://github.com/alfio-event/alf.io/issues/100)
- Paypal integration [\#77](https://github.com/alfio-event/alf.io/issues/77)
- Markdown support [\#9](https://github.com/alfio-event/alf.io/issues/9)
- Paypal integration [\#145](https://github.com/alfio-event/alf.io/pull/145) ([syjer](https://github.com/syjer))

**Fixed bugs:**

- Languages always mandatory in donation options [\#190](https://github.com/alfio-event/alf.io/issues/190)
- Numeric fields under the “Seats and payment info” allow negative numbers [\#189](https://github.com/alfio-event/alf.io/issues/189)
- Event begin date cannot be in the past [\#173](https://github.com/alfio-event/alf.io/issues/173)
- Graphs cannot be drawn when data is empty [\#172](https://github.com/alfio-event/alf.io/issues/172)
- Change password doesn't work [\#170](https://github.com/alfio-event/alf.io/issues/170)
- calendar while editing an existing event goes outside the viewport [\#169](https://github.com/alfio-event/alf.io/issues/169)
- .ics file has an error with new line character [\#168](https://github.com/alfio-event/alf.io/issues/168)
- Send invitations with CSV uses the wrong language [\#167](https://github.com/alfio-event/alf.io/issues/167)
- bug in master: cannot add new category in a existing event [\#161](https://github.com/alfio-event/alf.io/issues/161)
- bug in master: cannot change price of category [\#160](https://github.com/alfio-event/alf.io/issues/160)
- Cannot edit an event containing strange characters in the url [\#150](https://github.com/alfio-event/alf.io/issues/150)
- Show contextualized error message when ticket purchase doesn't work [\#147](https://github.com/alfio-event/alf.io/issues/147)

**Closed issues:**

- missing link to created event [\#194](https://github.com/alfio-event/alf.io/issues/194)
- Editing donation options causes duplication [\#191](https://github.com/alfio-event/alf.io/issues/191)
- Cannot delete a ticket category [\#188](https://github.com/alfio-event/alf.io/issues/188)
- Markdown preview: escape HTML [\#178](https://github.com/alfio-event/alf.io/issues/178)
- Allow markdown rendering to handle no-ops [\#139](https://github.com/alfio-event/alf.io/issues/139)
- Update Angular $tooltip to $uibTooltip [\#138](https://github.com/alfio-event/alf.io/issues/138)
- add mariadb in the travis matrix test [\#130](https://github.com/alfio-event/alf.io/issues/130)
- mysql porting v2 [\#98](https://github.com/alfio-event/alf.io/issues/98)
- add an additional field while editing an event [\#91](https://github.com/alfio-event/alf.io/issues/91)
- Mention contributors on the website [\#87](https://github.com/alfio-event/alf.io/issues/87)
- Updated Tutorial/Instructions [\#74](https://github.com/alfio-event/alf.io/issues/74)

## [1.8-RC2](https://github.com/alfio-event/alf.io/tree/1.8-RC2) (2016-09-05)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.8-RC1...1.8-RC2)

**Closed issues:**

- "An unexpected error has occurred. Please try again." trying to buy a paid ticket [\#193](https://github.com/alfio-event/alf.io/issues/193)

**Merged pull requests:**

- Add AWS Beanstalk support [\#187](https://github.com/alfio-event/alf.io/pull/187) ([madama](https://github.com/madama))
- Calendar fix [\#183](https://github.com/alfio-event/alf.io/pull/183) ([yankedev](https://github.com/yankedev))

## [1.8-RC1](https://github.com/alfio-event/alf.io/tree/1.8-RC1) (2016-08-28)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.7.4...1.8-RC1)

**Fixed bugs:**

- Warning message to select an organizer althought it is already selected [\#166](https://github.com/alfio-event/alf.io/issues/166)
- "ticket-has-changed-owner" email is sent unexpectedly [\#151](https://github.com/alfio-event/alf.io/issues/151)
- TicketReservationManager.countAvailableTickets count tickets with "PENDING" status as available [\#144](https://github.com/alfio-event/alf.io/issues/144)

**Closed issues:**

- Mysql errror : alter event table not working [\#165](https://github.com/alfio-event/alf.io/issues/165)
- Unable to create new event without image [\#157](https://github.com/alfio-event/alf.io/issues/157)
- More info about pending reservations [\#155](https://github.com/alfio-event/alf.io/issues/155)
- TicketReservationManager.deleteOfflinePayment does not reset categoryId on ticket for dynamic categories [\#146](https://github.com/alfio-event/alf.io/issues/146)
- Content Security Policy errors with style-src self [\#143](https://github.com/alfio-event/alf.io/issues/143)
- override general settings with machine-specific settings during development [\#137](https://github.com/alfio-event/alf.io/issues/137)
- gitignore Mac's `.DS\_Store` file [\#136](https://github.com/alfio-event/alf.io/issues/136)
- MySQL database setup fails \(invalid default timestamp value\) [\#131](https://github.com/alfio-event/alf.io/issues/131)
- set max file size for attachments [\#128](https://github.com/alfio-event/alf.io/issues/128)
- backend Android app:  login failure [\#125](https://github.com/alfio-event/alf.io/issues/125)

**Merged pull requests:**

- \#128 upload file lime 1mb added [\#164](https://github.com/alfio-event/alf.io/pull/164) ([Praitheesh](https://github.com/Praitheesh))
- split fullname \#102 [\#163](https://github.com/alfio-event/alf.io/pull/163) ([syjer](https://github.com/syjer))
- fix \#148: Improve VAT/Price management [\#149](https://github.com/alfio-event/alf.io/pull/149) ([cbellone](https://github.com/cbellone))
- replace data-tooltip with data-uib-tooltip \#138 [\#142](https://github.com/alfio-event/alf.io/pull/142) ([bunsenmcdubbs](https://github.com/bunsenmcdubbs))
- Allow markdown noop \#139 [\#141](https://github.com/alfio-event/alf.io/pull/141) ([bunsenmcdubbs](https://github.com/bunsenmcdubbs))
- \#131 \#136 \#137 [\#132](https://github.com/alfio-event/alf.io/pull/132) ([bunsenmcdubbs](https://github.com/bunsenmcdubbs))

## [1.7.4](https://github.com/alfio-event/alf.io/tree/1.7.4) (2016-06-29)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.7.3...1.7.4)

**Fixed bugs:**

- "Someting went wrong" message on scan valid QR ticket [\#120](https://github.com/alfio-event/alf.io/issues/120)

**Closed issues:**

- Getting "invalid API key", not sure which one [\#126](https://github.com/alfio-event/alf.io/issues/126)
- Event creation issue with 1.7.3 [\#124](https://github.com/alfio-event/alf.io/issues/124)
- Add change password functionality [\#122](https://github.com/alfio-event/alf.io/issues/122)
- Cannot create new events [\#119](https://github.com/alfio-event/alf.io/issues/119)
- Does alf support emitting invoices?  [\#104](https://github.com/alfio-event/alf.io/issues/104)
- Improve payment form [\#61](https://github.com/alfio-event/alf.io/issues/61)
- keep an eye on klimpr.com [\#6](https://github.com/alfio-event/alf.io/issues/6)

**Merged pull requests:**

- add note about install lombok plugin and autowired ide errors [\#129](https://github.com/alfio-event/alf.io/pull/129) ([bunsenmcdubbs](https://github.com/bunsenmcdubbs))
- \#117 - Add logout functionality [\#121](https://github.com/alfio-event/alf.io/pull/121) ([pgranato](https://github.com/pgranato))

## [1.7.3](https://github.com/alfio-event/alf.io/tree/1.7.3) (2016-04-26)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.7.2...1.7.3)

## [1.7.2](https://github.com/alfio-event/alf.io/tree/1.7.2) (2016-04-21)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.7.2-RC1...1.7.2)

**Fixed bugs:**

- Login issues on Heroku [\#116](https://github.com/alfio-event/alf.io/issues/116)

## [1.7.2-RC1](https://github.com/alfio-event/alf.io/tree/1.7.2-RC1) (2016-04-08)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.7.1...1.7.2-RC1)

**Implemented enhancements:**

- New API for sponsors [\#107](https://github.com/alfio-event/alf.io/issues/107)

**Fixed bugs:**

- MySql - invalid statement \(syntax error\) [\#108](https://github.com/alfio-event/alf.io/issues/108)

**Closed issues:**

- Cannot create event [\#113](https://github.com/alfio-event/alf.io/issues/113)
- Running behind proxy? [\#105](https://github.com/alfio-event/alf.io/issues/105)

## [1.7.1](https://github.com/alfio-event/alf.io/tree/1.7.1) (2016-02-16)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.7...1.7.1)

**Fixed bugs:**

- regression: cloud foundry support is broken [\#101](https://github.com/alfio-event/alf.io/issues/101)

## [1.7](https://github.com/alfio-event/alf.io/tree/1.7) (2016-02-13)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.6.2...1.7)

**Implemented enhancements:**

- mysql porting [\#90](https://github.com/alfio-event/alf.io/issues/90)

**Closed issues:**

- simplify/fix email handling [\#93](https://github.com/alfio-event/alf.io/issues/93)

## [1.6.2](https://github.com/alfio-event/alf.io/tree/1.6.2) (2016-01-28)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.6.1...1.6.2)

**Closed issues:**

- Video source is empty in online checkin [\#96](https://github.com/alfio-event/alf.io/issues/96)
- Search [\#94](https://github.com/alfio-event/alf.io/issues/94)

**Merged pull requests:**

- Dutch lang [\#95](https://github.com/alfio-event/alf.io/pull/95) ([mg-1999](https://github.com/mg-1999))

## [1.6.1](https://github.com/alfio-event/alf.io/tree/1.6.1) (2015-11-28)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.6...1.6.1)

## [1.6](https://github.com/alfio-event/alf.io/tree/1.6) (2015-11-22)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.5.2...1.6)

**Implemented enhancements:**

- REST APIs [\#86](https://github.com/alfio-event/alf.io/issues/86)
- Create link to external events [\#85](https://github.com/alfio-event/alf.io/issues/85)
- GUI support for multitenancy [\#62](https://github.com/alfio-event/alf.io/issues/62)
- support generic input/textarea field when assigning a ticket [\#46](https://github.com/alfio-event/alf.io/issues/46)

**Fixed bugs:**

- PostSQL error [\#82](https://github.com/alfio-event/alf.io/issues/82)
- Cant load scan page video source [\#79](https://github.com/alfio-event/alf.io/issues/79)

**Closed issues:**

- lock the ticket during check-in process [\#89](https://github.com/alfio-event/alf.io/issues/89)
- Add "express checkout" option [\#55](https://github.com/alfio-event/alf.io/issues/55)

## [1.5.2](https://github.com/alfio-event/alf.io/tree/1.5.2) (2015-10-20)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.5.1...1.5.2)

**Closed issues:**

- Language doesn't transfer when switching page. [\#80](https://github.com/alfio-event/alf.io/issues/80)
- Add new language [\#78](https://github.com/alfio-event/alf.io/issues/78)
- Cant insert address [\#76](https://github.com/alfio-event/alf.io/issues/76)

**Merged pull requests:**

- Add dutch language [\#83](https://github.com/alfio-event/alf.io/pull/83) ([mg-1999](https://github.com/mg-1999))

## [1.5.1](https://github.com/alfio-event/alf.io/tree/1.5.1) (2015-09-22)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.5...1.5.1)

## [1.5](https://github.com/alfio-event/alf.io/tree/1.5) (2015-09-16)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.4.1...1.5)

**Implemented enhancements:**

- Add docker support to alf.io [\#65](https://github.com/alfio-event/alf.io/issues/65)
- Generate and attach pdf payment receipt to confirmation email \(only when the user has effectively paid\) [\#54](https://github.com/alfio-event/alf.io/issues/54)
- In the qrcode for the check in operator, expose the full site info [\#53](https://github.com/alfio-event/alf.io/issues/53)
- Create "catch all" categories [\#50](https://github.com/alfio-event/alf.io/issues/50)
- put the qrcode in the upper right corner of the pdf [\#48](https://github.com/alfio-event/alf.io/issues/48)
- add image upload support for event logo [\#47](https://github.com/alfio-event/alf.io/issues/47)
- support add to calendar feature [\#43](https://github.com/alfio-event/alf.io/issues/43)
- support waiting queue [\#39](https://github.com/alfio-event/alf.io/issues/39)
- support multi language event and ticket category description [\#38](https://github.com/alfio-event/alf.io/issues/38)
- Add event name [\#35](https://github.com/alfio-event/alf.io/issues/35)

**Closed issues:**

- Error when using "Send custom message..." [\#72](https://github.com/alfio-event/alf.io/issues/72)
- Can not cancel paid ticket [\#71](https://github.com/alfio-event/alf.io/issues/71)
- Bug creating new organizations [\#70](https://github.com/alfio-event/alf.io/issues/70)
- Error while doing the environment setup [\#57](https://github.com/alfio-event/alf.io/issues/57)
- Create settings.properties [\#52](https://github.com/alfio-event/alf.io/issues/52)
- Export ticket details [\#42](https://github.com/alfio-event/alf.io/issues/42)
- add quartz scheduler for cluster aware job [\#73](https://github.com/alfio-event/alf.io/issues/73)
- translations [\#68](https://github.com/alfio-event/alf.io/issues/68)
- Cookie-law compliance [\#67](https://github.com/alfio-event/alf.io/issues/67)
- GUI UX/UI redesign [\#66](https://github.com/alfio-event/alf.io/issues/66)
- support multi tenancy [\#56](https://github.com/alfio-event/alf.io/issues/56)
- add mailchimp integration [\#36](https://github.com/alfio-event/alf.io/issues/36)

**Merged pull requests:**

- Improved translations [\#75](https://github.com/alfio-event/alf.io/pull/75) ([patbaumgartner](https://github.com/patbaumgartner))
- Ajusted translation from SIE to DU, translated  new text blocks [\#69](https://github.com/alfio-event/alf.io/pull/69) ([patbaumgartner](https://github.com/patbaumgartner))
- German translation [\#64](https://github.com/alfio-event/alf.io/pull/64) ([patbaumgartner](https://github.com/patbaumgartner))
- 1.3 maintenance [\#49](https://github.com/alfio-event/alf.io/pull/49) ([apolci](https://github.com/apolci))

## [1.4.1](https://github.com/alfio-event/alf.io/tree/1.4.1) (2015-04-07)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.4...1.4.1)

## [1.4](https://github.com/alfio-event/alf.io/tree/1.4) (2015-04-07)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/1.4-RC2...1.4)

**Implemented enhancements:**

- Shrink active category [\#45](https://github.com/alfio-event/alf.io/issues/45)
- reminder e-mail before event [\#24](https://github.com/alfio-event/alf.io/issues/24)
- check-in application [\#18](https://github.com/alfio-event/alf.io/issues/18)

**Fixed bugs:**

- \(admin\) Fix default Payment Type [\#44](https://github.com/alfio-event/alf.io/issues/44)

**Closed issues:**

- Show ticket details on admin page [\#41](https://github.com/alfio-event/alf.io/issues/41)

## [1.4-RC2](https://github.com/alfio-event/alf.io/tree/1.4-RC2) (2015-04-06)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/alfio-1.3.3...1.4-RC2)

## [alfio-1.3.3](https://github.com/alfio-event/alf.io/tree/alfio-1.3.3) (2015-03-07)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/alfio-1.3.2...alfio-1.3.3)

**Implemented enhancements:**

- add https://coveralls.io/ integration [\#19](https://github.com/alfio-event/alf.io/issues/19)

## [alfio-1.3.2](https://github.com/alfio-event/alf.io/tree/alfio-1.3.2) (2015-03-06)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/alfio-1.3.1...alfio-1.3.2)

## [alfio-1.3.1](https://github.com/alfio-event/alf.io/tree/alfio-1.3.1) (2015-03-01)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/alfio-1.3...alfio-1.3.1)

## [alfio-1.3](https://github.com/alfio-event/alf.io/tree/alfio-1.3) (2015-02-28)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/alfio-1.3-beta1...alfio-1.3)

**Implemented enhancements:**

- redesign/refactoring of configuration GUI [\#16](https://github.com/alfio-event/alf.io/issues/16)
- Create email sending queue [\#13](https://github.com/alfio-event/alf.io/issues/13)

**Closed issues:**

- export attendees' data [\#37](https://github.com/alfio-event/alf.io/issues/37)
- send invitation e-mail with reserved code [\#34](https://github.com/alfio-event/alf.io/issues/34)
- REST API for checkin app [\#25](https://github.com/alfio-event/alf.io/issues/25)

**Merged pull requests:**

- Added gradle support. [\#33](https://github.com/alfio-event/alf.io/pull/33) ([aalmiray](https://github.com/aalmiray))

## [alfio-1.3-beta1](https://github.com/alfio-event/alf.io/tree/alfio-1.3-beta1) (2015-01-18)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/alfio-1.2...alfio-1.3-beta1)

**Fixed bugs:**

- Validation is not triggered on page load [\#10](https://github.com/alfio-event/alf.io/issues/10)

**Closed issues:**

- Update category name doesn't work [\#32](https://github.com/alfio-event/alf.io/issues/32)

## [alfio-1.2](https://github.com/alfio-event/alf.io/tree/alfio-1.2) (2015-01-13)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/alfio-1.1...alfio-1.2)

**Implemented enhancements:**

- promo codes [\#29](https://github.com/alfio-event/alf.io/issues/29)

**Fixed bugs:**

- updating the price of a ticket category update only one of the two price related column in the ticket entity [\#31](https://github.com/alfio-event/alf.io/issues/31)
- Wrong price percentage calculation when creating an event with VAT excluded [\#30](https://github.com/alfio-event/alf.io/issues/30)

## [alfio-1.1](https://github.com/alfio-event/alf.io/tree/alfio-1.1) (2014-12-31)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/alfio-1.0...alfio-1.1)

**Implemented enhancements:**

- Manual payment processing [\#22](https://github.com/alfio-event/alf.io/issues/22)
- use mailgun's REST apis [\#15](https://github.com/alfio-event/alf.io/issues/15)
- In admin, event page: sort token by use, name, for ticket, sort by time [\#11](https://github.com/alfio-event/alf.io/issues/11)

**Fixed bugs:**

- reassign ticket from a restricted category to another one [\#26](https://github.com/alfio-event/alf.io/issues/26)

**Closed issues:**

- complete CSP headers [\#28](https://github.com/alfio-event/alf.io/issues/28)
- split process URLs [\#27](https://github.com/alfio-event/alf.io/issues/27)
- Support asynchronous payment flows [\#23](https://github.com/alfio-event/alf.io/issues/23)
- Generate accessible ticket PDF [\#2](https://github.com/alfio-event/alf.io/issues/2)

## [alfio-1.0](https://github.com/alfio-event/alf.io/tree/alfio-1.0) (2014-12-14)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/v1.0-pre-rename-v5...alfio-1.0)

**Implemented enhancements:**

- Add "additional info for organizer" [\#21](https://github.com/alfio-event/alf.io/issues/21)
- insert "expired on..." on expired categories [\#17](https://github.com/alfio-event/alf.io/issues/17)
- Partial editing of event [\#14](https://github.com/alfio-event/alf.io/issues/14)

**Fixed bugs:**

- TicketRepository.freeFromReservation does not clear up special\_price\_id\_fk column [\#12](https://github.com/alfio-event/alf.io/issues/12)

**Closed issues:**

- check the .sql creation script and add the missing index [\#20](https://github.com/alfio-event/alf.io/issues/20)
- Create admin area [\#1](https://github.com/alfio-event/alf.io/issues/1)

## [v1.0-pre-rename-v5](https://github.com/alfio-event/alf.io/tree/v1.0-pre-rename-v5) (2014-11-14)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/v1.0-pre-rename-v4...v1.0-pre-rename-v5)

## [v1.0-pre-rename-v4](https://github.com/alfio-event/alf.io/tree/v1.0-pre-rename-v4) (2014-11-11)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/v1.0-pre-rename-v3...v1.0-pre-rename-v4)

## [v1.0-pre-rename-v3](https://github.com/alfio-event/alf.io/tree/v1.0-pre-rename-v3) (2014-11-10)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/v1.0-pre-rename-v2...v1.0-pre-rename-v3)

## [v1.0-pre-rename-v2](https://github.com/alfio-event/alf.io/tree/v1.0-pre-rename-v2) (2014-11-10)
[Full Changelog](https://github.com/alfio-event/alf.io/compare/v1.0-pre-rename...v1.0-pre-rename-v2)

## [v1.0-pre-rename](https://github.com/alfio-event/alf.io/tree/v1.0-pre-rename) (2014-11-09)
**Implemented enhancements:**

- allow free event creation [\#5](https://github.com/alfio-event/alf.io/issues/5)

**Closed issues:**

- Handle correctly the timezone of a event [\#8](https://github.com/alfio-event/alf.io/issues/8)
- https handling [\#7](https://github.com/alfio-event/alf.io/issues/7)
- configure payment methods [\#4](https://github.com/alfio-event/alf.io/issues/4)
- integrate stripe.com [\#3](https://github.com/alfio-event/alf.io/issues/3)



\* *This Change Log was automatically generated by [github_changelog_generator](https://github.com/skywinder/Github-Changelog-Generator)*