--
-- This file is part of bagarino.
--
-- bagarino is free software: you can redistribute it and/or modify
-- it under the terms of the GNU General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- bagarino is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU General Public License for more details.
--
-- You should have received a copy of the GNU General Public License
-- along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
--

INSERT INTO organization(name, description, email) VALUES ('demo', 'demo organization', 'info@pippobaudo.com');

insert into event(description, short_name, website_url, website_t_c_url, location, latitude, longitude, start_ts, end_ts, regular_price_cts, currency, available_seats, vat_included, vat, allowed_payment_proxies, private_key, org_id, time_zone, image_url)
  values('event desc', 'eventname', 'http://localhost:8080', 'http://localhost:8080', 'demo location', '0', '0', '2015-01-10 05:00:00' , '2015-01-11 04:59:00' , 1000, 'CHF', 440, 'true', 8, 'STRIPE', 'bagarino-rulez', 0, 'America/New_York', 'http://localhost:8080/resources/images/sample-logo.png');

insert into ticket_category(inception, expiration, name, description, max_tickets, price_cts, access_restricted, tc_status, event_id) values
  ('2014-01-10 00:00:00', '2015-10-10 00:00:00', 'Normal', 'Very good category', 2, 0, false, 'ACTIVE', 0),
  ('2014-01-10 00:00:00', '2015-10-10 00:00:00', 'Not normal', 'Very very good category', 5, 463, false, 'ACTIVE', 0),
  ('2015-09-10 00:00:00', '2015-10-10 00:00:00', 'Still not in sale', 'Last day category', 1, 556, false, 'ACTIVE', 0),
  ('2014-01-01 00:00:00', '2014-09-01 00:00:00', 'Expired', 'Expired', 1, 400, false, 'ACTIVE', 0),
  ('2014-01-10 00:00:00', '2015-10-10 00:00:00', 'Restricted', 'Very very good category', 4, 463, true, 'ACTIVE', 0);

insert into tickets_reservation (id, validity, status, full_name, email_address, billing_address) values('abcdefghi', '2014-01-10 00:00:00', 'IN_PAYMENT', 'ciccio', 'cc@cc.uu', null);
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

  -- still not in sale
  ('abcdefghilmo45', '2014-01-10 00:00:00', 2, 0, 'FREE', 463, 463, null),
  
  --expired
  ('abcdefghilmo46', '2014-01-10 00:00:00', 3, 0, 'FREE', 463, 463, null),
  
  --restricted
  ('abcdefghilmo47', '2014-01-10 00:00:00', 4, 0, 'FREE', 463, 463, null),
  ('abcdefghilmo48', '2014-01-10 00:00:00', 4, 0, 'FREE', 463, 463, null);

insert into special_price (code, price_cts, ticket_category_id, status) values
  ('424242', 463, 4, 'FREE'),
  ('424243', 463, 4, 'CANCELLED'); 
  
insert into configuration (c_key, c_value, description) values
	('STRIPE_SECRET_KEY', 'sk_test_cayJOFUUYF9cWOoMXemJd61Z', 'Stripe''s secret key'),
	('STRIPE_PUBLIC_KEY', 'pk_test_gY3X0UiTgKCeStUG67i2kEFq', 'Stripe''s public key'),
	('BASE_URL', 'http://localhost:8080/', 'Base application url');
