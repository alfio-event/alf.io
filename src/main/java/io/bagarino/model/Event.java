/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.bagarino.datamapper.ConstructorAnnotationRowMapper.Column;
import io.bagarino.model.transaction.PaymentProxy;
import io.bagarino.util.MonetaryUtil;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

@Getter
public class Event {
    private final int id;
    private final String shortName;
    private final String description;
    private final String location;
    private final String latitude;
    private final String longitude;
    private final LocalDateTime begin;
    private final LocalDateTime end;
    private final int regularPriceInCents;
    private final String currency;
    private final int availableSeats;
    private final boolean vatIncluded;
    private final BigDecimal vat;
    private final List<PaymentProxy> allowedPaymentProxies;
    private final String privateKey;
    private final ZoneId timeZone;


    public Event(@Column("id") int id,
                 @Column("short_name") String shortName,
                 @Column("description") String description,
                 @Column("location") String location,
                 @Column("latitude") String latitude,
                 @Column("longitude") String longitude,
                 @Column("start_ts") Date begin,
                 @Column("end_ts") Date end,
                 @Column("time_zone") String timeZone,
                 @Column("regular_price_cts") int regularPriceInCents,
                 @Column("currency") String currency,
                 @Column("available_seats") int availableSeats,
                 @Column("vat_included") boolean vatIncluded,
                 @Column("vat") BigDecimal vat,
                 @Column("allowed_payment_proxies") String allowedPaymentProxies,
                 @Column("private_key") String privateKey) {
        this.id = id;
        this.shortName = shortName;
        this.description = description;
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
        this.begin = LocalDateTime.ofInstant(begin.toInstant(), ZoneId.of("UTC"));
        this.end = LocalDateTime.ofInstant(end.toInstant(), ZoneId.of("UTC"));
        this.timeZone = TimeZone.getTimeZone(timeZone).toZoneId();
        this.regularPriceInCents = regularPriceInCents;
        this.currency = currency;
        this.availableSeats = availableSeats;
        this.vatIncluded = vatIncluded;
        this.vat = vat;
        this.privateKey = privateKey;
        this.allowedPaymentProxies = Arrays.stream(allowedPaymentProxies.split(","))
                .filter(StringUtils::isNotBlank)
                .map(PaymentProxy::valueOf)
                .collect(Collectors.toList());
    }

    public BigDecimal getRegularPrice() {
        return MonetaryUtil.centsToUnit(regularPriceInCents);
    }
    
    
    public boolean getSameDay() {
    	return begin.atZone(timeZone).equals(end.atZone(timeZone));
    }

    @JsonIgnore
    public String getPrivateKey() {
        return privateKey;
    }

    /**
     * Returns the begin date in the event's timezone
     * @return Date
     */
    public Date getBegin() {
        return Date.from(begin.atZone(timeZone).toInstant());
    }

    /**
     * Returns the end date in the event's timezone
     * @return Date
     */
    public Date getEnd() {
        return Date.from(end.atZone(timeZone).toInstant());
    }

    /**
     * Returns a string representation of the event's time zone
     * @return timeZone
     */
    public String getTimeZone() {
        return timeZone.toString();
    }

    @JsonIgnore
    public ZoneId getZoneId() {
        return timeZone;
    }
}
