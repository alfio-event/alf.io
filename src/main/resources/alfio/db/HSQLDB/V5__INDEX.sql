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

-- add missing indexes
CREATE INDEX "event_org_id_idx" ON EVENT(ORG_ID);
CREATE INDEX "ticket_category_event_id_idx" ON TICKET_CATEGORY(EVENT_ID);

CREATE INDEX "ticket_category_id_idx" ON TICKET(CATEGORY_ID);
CREATE INDEX "ticket_event_id_idx" ON TICKET(EVENT_ID);
CREATE INDEX "ticket_tickets_reservation_id_idx" ON TICKET(TICKETS_RESERVATION_ID);

CREATE INDEX "j_user_organization_user_id_idx" ON J_USER_ORGANIZATION(USER_ID);
CREATE INDEX "j_user_organization_org_id_idx" ON J_USER_ORGANIZATION(ORG_ID);

CREATE INDEX "b_transaction_reservation_id_idx" ON B_TRANSACTION(RESERVATION_ID);