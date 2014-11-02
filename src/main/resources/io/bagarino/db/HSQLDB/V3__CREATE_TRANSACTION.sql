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

create table b_transaction (
  id integer identity not null,
  gtw_tx_id varchar(2048) not null,
  reservation_id varchar(2048) not null,
  t_timestamp timestamp with time zone not null,
  price_cts integer not null,
  currency varchar(255) not null,
  description varchar(2048) not null,
  payment_proxy varchar(2048) not null
);