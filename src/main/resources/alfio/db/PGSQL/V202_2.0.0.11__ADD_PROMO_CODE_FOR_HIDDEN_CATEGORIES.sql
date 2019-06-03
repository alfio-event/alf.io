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

alter table promo_code add column code_type varchar(255) not null default 'DISCOUNT';
alter table promo_code add column hidden_category_id integer;
alter table promo_code add constraint if_access_code_then_hidden_category_not_null
    CHECK ( (code_type <> 'ACCESS' ) OR (hidden_category_id is not null) );
alter table promo_code drop constraint "check_discount_type";
alter table promo_code add constraint "check_discount_type" check (discount_type = 'FIXED_AMOUNT' OR discount_type = 'PERCENTAGE' or discount_type = 'NONE');

alter table special_price add column access_code_id_fk integer;
alter table special_price add constraint "access_code_promo_code_id" foreign key (access_code_id_fk) references promo_code(id);

CREATE OR REPLACE FUNCTION trf_check_access_code_allocation()
    RETURNS TRIGGER AS
$body$
DECLARE
    r_count numeric;
BEGIN
    IF (NEW.access_code_id_fk is not null) THEN
        -- serialize by locking all tokens for the current category
        PERFORM * from special_price where ticket_category_id = NEW.ticket_category_id for update;
        r_count = (select count(*) from special_price where access_code_id_fk = NEW.access_code_id_fk);
        PERFORM max_usage from promo_code where id = NEW.access_code_id_fk and max_usage is not null and
               max_usage < r_count;
        IF FOUND THEN
            raise EXCEPTION USING MESSAGE = ('Max usage exceeded. Tokens requested: ' || r_count);
        END IF;
    END IF;
    RETURN NULL;
END
$body$
    LANGUAGE plpgsql;

CREATE TRIGGER tr_check_access_code_allocation
    AFTER UPDATE ON special_price
    FOR EACH ROW EXECUTE PROCEDURE trf_check_access_code_allocation();
