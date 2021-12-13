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

-- these update makes sure that the VAT_STATUS constraint doesn't fail

update ticket set FULL_NAME = null, EMAIL_ADDRESS = null, SPECIAL_PRICE_ID_FK = null,
                  LOCKED_ASSIGNMENT = false, USER_LANGUAGE = null, REMINDER_SENT = false,
                  SRC_PRICE_CTS = 0, FINAL_PRICE_CTS = 0, VAT_CTS = 0, DISCOUNT_CTS = 0,
                  FIRST_NAME = null, LAST_NAME = null, EXT_REFERENCE = null,
                  TAGS = array[]::text[], METADATA = '{}'::jsonb
    where tickets_reservation_id is null and final_price_cts <> 0;