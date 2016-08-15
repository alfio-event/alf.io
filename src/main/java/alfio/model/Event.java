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

import alfio.model.transaction.PaymentProxy;
import alfio.util.MonetaryUtil;
import biweekly.ICalVersion;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.io.text.ICalWriter;
import biweekly.property.Organizer;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Getter
@Log4j2
public class Event implements EventHiddenFieldContainer {
    public enum EventType {
        INTERNAL, EXTERNAL
    }
    private final int id;
    private final EventType type;
    private final String shortName;
    private final String displayName;
    private final String websiteUrl;
    private final String externalUrl;
    private final String termsAndConditionsUrl;
    private final String imageUrl;
    private final String fileBlobId;
    private final String location;
    private final String latitude;
    private final String longitude;
    private final ZonedDateTime begin;
    private final ZonedDateTime end;
    private final String currency;
    private final int availableSeats;
    private final boolean vatIncluded;
    private final BigDecimal vat;
    private final List<PaymentProxy> allowedPaymentProxies;
    private final String privateKey;
    private final int organizationId;
    private final ZoneId timeZone;
    private final int locales;

    private final int srcPriceCts;
    private final PriceContainer.VatStatus vatStatus;


    public Event(@Column("id") int id,
                 @Column("type") EventType type,
                 @Column("short_name") String shortName,
                 @Column("display_name") String displayName,
                 @Column("location") String location,
                 @Column("latitude") String latitude,
                 @Column("longitude") String longitude,
                 @Column("start_ts") ZonedDateTime begin,
                 @Column("end_ts") ZonedDateTime end,
                 @Column("time_zone") String timeZone,
                 @Column("website_url") String websiteUrl,
                 @Column("external_url") String externalUrl,
                 @Column("file_blob_id") String fileBlobId,
                 @Column("website_t_c_url") String termsAndConditionsUrl,
                 @Column("image_url") String imageUrl,
                 @Column("currency") String currency,
                 @Column("available_seats") int availableSeats,
                 @Column("vat") BigDecimal vat,
                 @Column("allowed_payment_proxies") String allowedPaymentProxies,
                 @Column("private_key") String privateKey,
                 @Column("org_id") int organizationId,
                 @Column("locales") int locales,
                 @Column("src_price_cts") int srcPriceInCents,
                 @Column("vat_status") PriceContainer.VatStatus vatStatus) {

        this.type = type;
        this.displayName = displayName;
        this.websiteUrl = websiteUrl;
        this.externalUrl = externalUrl;
        this.termsAndConditionsUrl = termsAndConditionsUrl;
        this.imageUrl = imageUrl;
        this.fileBlobId = fileBlobId;

        final ZoneId zoneId = TimeZone.getTimeZone(timeZone).toZoneId();
        this.id = id;
        this.shortName = shortName;
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timeZone = zoneId;
        this.begin = begin.withZoneSameInstant(zoneId);
        this.end = end.withZoneSameInstant(zoneId);
        this.currency = currency;
        this.availableSeats = availableSeats;
        this.vatIncluded = vatStatus == PriceContainer.VatStatus.INCLUDED;
        this.vat = vat;
        this.privateKey = privateKey;
        this.organizationId = organizationId;
        this.locales = locales;
        this.allowedPaymentProxies = Arrays.stream(Optional.ofNullable(allowedPaymentProxies).orElse("").split(","))
                .filter(StringUtils::isNotBlank)
                .map(PaymentProxy::valueOf)
                .collect(Collectors.toList());
        this.vatStatus = vatStatus;
        this.srcPriceCts = srcPriceInCents;
    }

    public BigDecimal getRegularPrice() {
        return MonetaryUtil.centsToUnit(srcPriceCts);
    }
    
    
    public boolean getSameDay() {
        return begin.truncatedTo(ChronoUnit.DAYS).equals(end.truncatedTo(ChronoUnit.DAYS));
    }

    @Override
    @JsonIgnore
    public String getPrivateKey() {
        return privateKey;
    }
    
    @Override
    @JsonIgnore
    public Pair<String, String> getLatLong() {
        return Pair.of(latitude, longitude);
    }

    /**
     * Returns the begin date in the event's timezone
     * @return Date
     */
    public ZonedDateTime getBegin() {
        return begin;
    }

    /**
     * Returns the end date in the event's timezone
     * @return Date
     */
    public ZonedDateTime getEnd() {
        return end;
    }

    /**
     * Returns a string representation of the event's time zone
     * @return timeZone
     */
    public String getTimeZone() {
        return timeZone.toString();
    }

    @Override
    @JsonIgnore
    public ZoneId getZoneId() {
        return timeZone;
    }

    public boolean isFreeOfCharge() {
        return srcPriceCts == 0;
    }

    public boolean getFree() {
        return isFreeOfCharge();
    }
    
    public boolean getImageIsPresent() {
        return StringUtils.isNotBlank(imageUrl) || StringUtils.isNotBlank(fileBlobId);
    }

    public boolean getFileBlobIdIsPresent() {
        return StringUtils.isNotBlank(fileBlobId);
    }

    public boolean getMultiplePaymentMethods() {
        return allowedPaymentProxies.size() > 1;
    }

    public PaymentProxy getFirstPaymentMethod() {
        return allowedPaymentProxies.isEmpty() ? null : allowedPaymentProxies.get(0);//it is guaranteed that this list is not null. 
    }

    public boolean supportsPaymentMethod(PaymentProxy paymentProxy) {
        return allowedPaymentProxies.contains(paymentProxy);
    }

    public List<ContentLanguage> getContentLanguages() {
        return ContentLanguage.findAllFor(getLocales());
    }

    @Override
    @JsonIgnore
    public String getGoogleCalendarUrl() {
        return getGoogleCalendarUrl("");//used by the email
    }

    @Override
    @JsonIgnore
    public String getGoogleCalendarUrl(String description) {
        //format described at http://stackoverflow.com/a/19867654
        // sprop does not seems to have any effect http://useroffline.blogspot.ch/2009/06/making-google-calendar-link.html
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyMMdd'T'HHmmss");
        return UriComponentsBuilder.fromUriString("https://www.google.com/calendar/event")
                .queryParam("action", "TEMPLATE")
                .queryParam("dates", getBegin().format(formatter) + "/" + getEnd().format(formatter))
                .queryParam("ctz", getTimeZone())
                .queryParam("text", getDisplayName())
                .queryParam("details", description)
                .queryParam("location", getLocation())
                .toUriString();
    }

    @Override
    @JsonIgnore
    public Optional<byte[]> getIcal(String description, String organizerName, String organizerEmail) {
        ICalendar ical = new ICalendar();
        VEvent vEvent = new VEvent();
        vEvent.setSummary(getDisplayName());
        vEvent.setDescription(description);
        vEvent.setLocation(getLocation());
        vEvent.setDateStart(Date.from(getBegin().toInstant()));
        vEvent.setDateEnd(Date.from(getEnd().toInstant()));
        vEvent.setUrl(getWebsiteUrl());
        vEvent.setOrganizer(new Organizer(organizerName, organizerEmail));
        ical.addEvent(vEvent);
        StringWriter strWriter = new StringWriter();
        try (ICalWriter writer = new ICalWriter(strWriter, ICalVersion.V1_0)) {
            writer.write(ical);
            return Optional.of(strWriter.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("was not able to generate iCal for event " + getShortName(), e);
            return Optional.empty();
        }
    }

    public boolean isInternal() {
        return type == EventType.INTERNAL;
    }

}
