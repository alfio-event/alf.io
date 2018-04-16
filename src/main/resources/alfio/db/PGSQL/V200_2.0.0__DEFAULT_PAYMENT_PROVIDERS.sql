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

-- opinionated configuration, we activate everything is already available in v1

INSERT INTO CONFIGURATION (C_KEY, C_VALUE, DESCRIPTION) VALUES('BANK_TRANSFER_ENABLED', 'true', 'Bank Transfer Enabled');
INSERT INTO CONFIGURATION (C_KEY, C_VALUE, DESCRIPTION) VALUES('PAYPAL_ENABLED', 'true', 'PayPal Enabled');
INSERT INTO CONFIGURATION (C_KEY, C_VALUE, DESCRIPTION) VALUES('ON_SITE_ENABLED', 'true', 'On Site cash payment Enabled');
INSERT INTO CONFIGURATION (C_KEY, C_VALUE, DESCRIPTION) VALUES('STRIPE_CC_ENABLED', 'true', 'Stripe Credit Cards Enabled');
