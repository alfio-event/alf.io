--
-- This file is part of alf.io.
--
-- alf.io is free software: you can redistribute it and/or modify
-- it under the terms of the GNU General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- alf.io is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU General Public License for more details.
--
-- You should have received a copy of the GNU General Public License
-- along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
--

INSERT INTO organization(name, description, email) VALUES ('demo', 'demo organization', 'info@pippobaudo.com');

insert into event(short_name, website_url, website_t_c_url, location, latitude, longitude, start_ts, end_ts, regular_price_cts, currency, available_seats, vat_included, vat, allowed_payment_proxies, private_key, org_id, time_zone, image_url)
values('eventname', 'http://localhost:8080', 'http://localhost:8080', 'demo location', '0', '0', '2016-10-10 04:00:00' , '2016-10-11 03:59:00' , 1000, 'CHF', 20, 'true', 8, 'STRIPE,ON_SITE,OFFLINE,PAYPAL', 'alfio-uberall', 0, 'America/New_York', 'http://localhost:8080/resources/images/sample-logo.png');

insert into ticket_category(inception, expiration, name, max_tickets, price_cts, access_restricted, tc_status, event_id, bounded) values
  ('2014-01-10 00:00:00', '2016-10-10 00:00:00', 'Normal', 2, 0, false, 'ACTIVE', 0, true),
  ('2014-01-10 00:00:00', '2016-10-10 00:00:00', 'Not normal', 5, 463, false, 'ACTIVE', 0, TRUE ),
  ('2016-09-10 00:00:00', '2016-10-10 00:00:00', 'Still not in sale', 1, 556, false, 'ACTIVE', 0, TRUE ),
  ('2014-01-01 00:00:00', '2014-09-01 00:00:00', 'Expired', 1, 400, false, 'ACTIVE', 0, TRUE ),
  ('2014-01-10 00:00:00', '2016-10-10 00:00:00', 'Restricted', 4, 463, true, 'ACTIVE', 0, TRUE ),
  ('2014-01-10 00:00:00', '2016-10-10 00:00:00', 'Unbounded', -1, 463, false, 'ACTIVE', 0, FALSE );

insert into tickets_reservation (id, validity, status, full_name, email_address, billing_address) values('abcdefghi', '2014-01-10 00:00:00', 'IN_PAYMENT', 'ciccio', 'cc@cc.uu', null);
insert into tickets_reservation (id, validity, status, full_name, email_address, billing_address) values('abcdefghiz', '2014-01-10 00:00:00', 'PENDING', 'ciccio', 'cc@cc.uu', null);
insert into ticket (uuid, creation, category_id, event_id, status, original_price_cts, paid_price_cts, tickets_reservation_id)
values

-- free tickets
  ('abcdefghilmn', '2014-01-10 00:00:00', 0, 0, 'FREE', 0, 0, null),
  ('abcdefghilmo', '2014-01-10 00:00:00', 0, 0, 'FREE', 0, 0, null),

-- paid ticket
  ('abcdefghilmn41', '2014-01-10 00:00:00', 1, 0, 'FREE', 463, 463, null),
  ('abcdefghilmn42', '2014-01-10 00:00:00', 1, 0, 'FREE', 463, 463, null),
  ('abcdefghilmn43', '2014-01-10 00:00:00', 1, 0, 'FREE', 463, 463, null),
  ('abcdefghilmo44', '2014-01-10 00:00:00', 1, 0, 'FREE', 463, 463, null),
-- stuck reservation
  ('abcdefghilmo55', '2014-01-10 00:00:00', 1, 0, 'PENDING', 463, 463, 'abcdefghi'),
-- expired unbounded
  ('abcdefghilmo56', '2014-01-10 00:00:00', 5, 0, 'PENDING', 463, 463, 'abcdefghiz'),

--free unbounded
  ('abcdefghilmo57', '2014-01-10 00:00:00', NULL, 0, 'FREE', 463, 463, NULL),
  ('abcdefghilmo58', '2014-01-10 00:00:00', NULL, 0, 'FREE', 463, 463, NULL),
  ('abcdefghilmo59', '2014-01-10 00:00:00', NULL, 0, 'FREE', 463, 463, NULL),
  ('abcdefghilmo60', '2014-01-10 00:00:00', NULL, 0, 'FREE', 463, 463, NULL),
  ('abcdefghilmo61', '2014-01-10 00:00:00', NULL, 0, 'FREE', 463, 463, NULL),
  ('abcdefghilmo62', '2014-01-10 00:00:00', NULL, 0, 'FREE', 463, 463, NULL),

-- still not in sale
  ('abcdefghilmo45', '2014-01-10 00:00:00', 2, 0, 'FREE', 463, 463, null),

--expired
  ('abcdefghilmo46', '2014-01-10 00:00:00', 3, 0, 'FREE', 463, 463, null),

--restricted
  ('abcdefghilmo47', '2014-01-10 00:00:00', 4, 0, 'FREE', 463, 463, null),
  ('abcdefghilmo48', '2014-01-10 00:00:00', 4, 0, 'FREE', 463, 463, null),
  ('abcdefghilmo49', '2014-01-10 00:00:00', 4, 0, 'FREE', 463, 463, null),
  ('abcdefghilmo50', '2014-01-10 00:00:00', 4, 0, 'FREE', 463, 463, null);

insert into special_price (code, price_cts, ticket_category_id, status) values
  ('424242', 463, 4, 'FREE'),
  ('424243', 463, 4, 'FREE'),
  ('424244', 463, 4, 'FREE'),
  ('424245', 463, 4, 'CANCELLED');

insert into configuration (c_key, c_value, description) values
  ('STRIPE_SECRET_KEY', 'sk_test_cayJOFUUYF9cWOoMXemJd61Z', 'Stripe''s secret key'),
  ('STRIPE_PUBLIC_KEY', 'pk_test_gY3X0UiTgKCeStUG67i2kEFq', 'Stripe''s public key'),
  ('BASE_URL', 'http://localhost:8080/', 'Base application url'),
  ('SUPPORTED_LANGUAGES', '7', 'supported languages'),
  ('PAYPAL_CLIENT_ID', 'AQkquBDf1zctJOWGKWUEtKXm6qVhueUEMvXO_-MCI4DQQ4-LWvkDLIN2fGsd', 'Paypal REST API client ID'),
  ('PAYPAL_CLIENT_SECRET','EL1tVxAjhT7cJimnz5-Nsx9k2reTKSVfErNQF-CmrwJgxRtylkGTKlU4RvrX', 'Paypal REST API client secret'),
  ('PAYPAL_LIVE_MODE', 'false', 'Enable live mode for Paypal');


-- create fields configuration

insert into ticket_field_configuration (event_id_fk, field_name, field_order, field_type, field_required, field_maxlength)
    (SELECT event.id, 'jobTitle', 0, 'input:text', false, 255  FROM event where short_name = 'eventname');
insert into ticket_field_configuration (event_id_fk, field_name, field_order, field_type, field_required, field_maxlength)
    (SELECT event.id, 'company', 1, 'input:text', false, 255 FROM event where short_name = 'eventname');
insert into ticket_field_configuration (event_id_fk, field_name, field_order, field_type, field_required, field_maxlength)
    (SELECT event.id, 'phoneNumber', 2, 'input:tel', false, 255 FROM event where short_name = 'eventname');
insert into ticket_field_configuration (event_id_fk, field_name, field_order, field_type, field_required, field_maxlength)
    (SELECT event.id, 'address', 3, 'textarea', false, 450 FROM event where short_name = 'eventname');
insert into ticket_field_configuration (event_id_fk, field_name, field_order, field_type, field_required)
    (SELECT event.id, 'country', 4, 'country', false FROM event where short_name = 'eventname');
insert into ticket_field_configuration (event_id_fk, field_name, field_order, field_type, field_required, field_restricted_values)
    (SELECT event.id, 'gender', 5, 'select', false, '["F", "M", "X"]' FROM event where short_name = 'eventname');
insert into ticket_field_configuration (event_id_fk, field_name, field_order, field_type, field_required, field_restricted_values)
    (SELECT event.id, 'tShirtSize', 6, 'select', false, '["SMALL", "MEDIUM", "LARGE", "X-LARGE", "2X-LARGE"]' FROM event where short_name = 'eventname');
insert into ticket_field_configuration (event_id_fk, field_name, field_order, field_type, field_required, field_maxlength)
    (SELECT event.id, 'notes', 7, 'textarea', false, 1024 FROM event where short_name = 'eventname');

-- create translations for each fields
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'en', '{"label": "Job title"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'jobTitle'  and short_name = 'eventname';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'it', '{"label": "Ruolo"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'jobTitle'  and short_name = 'eventname';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'de', '{"label": "Berufsbezeichnung"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'jobTitle'  and short_name = 'eventname';

insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'en', '{"label": "Company"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'company'  and short_name = 'eventname';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'it', '{"label": "Societ\u00E0"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'company'  and short_name = 'eventname';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'de', '{"label": "Firma"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'company'  and short_name = 'eventname';

insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'en', '{"label": "Phone number"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'phoneNumber'  and short_name = 'eventname';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'it', '{"label": "Numero di telefono"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'phoneNumber'  and short_name = 'eventname';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'de', '{"label": "Telefonnummer"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'phoneNumber'  and short_name = 'eventname';

insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'en', '{"label": "Address"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'address'  and short_name = 'eventname';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'it', '{"label": "Indirizzo"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'address'  and short_name = 'eventname';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'de', '{"label": "Adresse"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'address'  and short_name = 'eventname';

insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'en', '{"label": "Country"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'country'  and short_name = 'eventname';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'it', '{"label": "Nazione"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'country'  and short_name = 'eventname';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'de', '{"label": "Land"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'country'  and short_name = 'eventname';

insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'en', '{"label": "I am", "restrictedValues": {"F" : "a woman", "M" : "a man", "X" : "Other"}}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'gender'  and short_name = 'eventname';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'it', '{"label": "Sono", "restrictedValues": {"F" : "una donna", "M" : "un uomo", "X" : "Altro"}}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'gender'  and short_name = 'eventname';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'de', '{"label": "Ich bin", "restrictedValues": {"F" : "Frau", "M" : "Herr", "X" : "Andere"}}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'gender'  and short_name = 'eventname';

insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'en', '{"label": "T-Shirt size", "restrictedValues": {"SMALL" : "Small", "MEDIUM" : "Medium", "LARGE" : "Large", "X-LARGE" : "X-Large", "2X-LARGE" : "2X-Large" }}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'tShirtSize'  and short_name = 'eventname';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'it', '{"label": "Misura T-Shirt", "restrictedValues": {"SMALL" : "Small", "MEDIUM" : "Medium", "LARGE" : "Large", "X-LARGE" : "X-Large", "2X-LARGE" : "2X-Large" }}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'tShirtSize'  and short_name = 'eventname';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'de', '{"label": "T-Shirt-Gr\u00F6\u00DFe", "restrictedValues": {"SMALL" : "Small", "MEDIUM" : "Medium", "LARGE" : "Large", "X-LARGE" : "X-Large", "2X-LARGE" : "2X-Large" }}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'tShirtSize'  and short_name = 'eventname';

insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'en', '{"label": "Notes", "placeholder" : "Should you have any special need (such as but not limited to diet or accessibility requirements) please let us know"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'notes'  and short_name = 'eventname';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'it', '{"label": "Note", "placeholder" : "Gerne nehmen wir auf Ihre Bed\u00FCrfnisse R\u00FCcksicht, falls du aussergew\u00F6hnliche Essensgewohnheiten oder eingeschr\u00E4nkte Zutrittsm\u00F6glichkeiten hast, informiere uns bitte."}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'notes'  and short_name = 'eventname';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select id, 'de', '{"label": "Bemerkungen", "placeholder" : "Hai qualche esigenza particolare (ad esempio per quanto riguarda cibo o accessibilit\u00E0)? Per favore, faccelo sapere!"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'notes'  and short_name = 'eventname';