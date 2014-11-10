/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.model;

import alfio.datamapper.ConstructorAnnotationRowMapper;
import lombok.Getter;

@Getter
public class SpecialPrice {

    public enum Status {
        WAITING, FREE, PENDING, TAKEN, CANCELLED
    }

    private final int id;
    private final String code;
    private final int priceInCents;
    private final int ticketCategoryId;
    private final Status status;

    public SpecialPrice(@ConstructorAnnotationRowMapper.Column("id") int id,
                        @ConstructorAnnotationRowMapper.Column("code") String code,
                        @ConstructorAnnotationRowMapper.Column("price_cts") int priceInCents,
                        @ConstructorAnnotationRowMapper.Column("ticket_category_id") int ticketCategoryId,
                        @ConstructorAnnotationRowMapper.Column("status") String status) {
        this.id = id;
        this.code = code;
        this.priceInCents = priceInCents;
        this.ticketCategoryId = ticketCategoryId;
        this.status = Status.valueOf(status);
    }
}
