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
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.flywaydb.core.api.MigrationVersion;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;

@Getter
@Log4j2
public class Event extends EventAndOrganizationId implements EventHiddenFieldContainer {

    private static final String VERSION_FOR_FIRST_AND_LAST_NAME = "15.1.8.8";

    public enum Status {
        DRAFT, PUBLIC, DISABLED
    }

    public enum EventType {
        INTERNAL, EXTERNAL
    }
    private final EventType type;
    private final String shortName;
    private final String displayName;
    private final String websiteUrl;
    private final String externalUrl;
    private final String termsAndConditionsUrl;
    private final String privacyPolicyUrl;
    private final String imageUrl;
    private final String fileBlobId;
    private final String location;
    private final String latitude;
    private final String longitude;
    private final ZonedDateTime begin;
    private final ZonedDateTime end;
    private final String currency;
    private final boolean vatIncluded;
    private final BigDecimal vat;
    private final List<PaymentProxy> allowedPaymentProxies;

    @JsonIgnore
    private final String privateKey;
    private final ZoneId timeZone;
    private final int locales;

    private final int srcPriceCts;
    private final PriceContainer.VatStatus vatStatus;
    private final String version;
    private final Status status;



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
                 @Column("website_p_p_url") String privacyPolicyUrl,
                 @Column("image_url") String imageUrl,
                 @Column("currency") String currency,
                 @Column("vat") BigDecimal vat,
                 @Column("allowed_payment_proxies") String allowedPaymentProxies,
                 @Column("private_key") String privateKey,
                 @Column("org_id") int organizationId,
                 @Column("locales") int locales,
                 @Column("src_price_cts") int srcPriceInCents,
                 @Column("vat_status") PriceContainer.VatStatus vatStatus,
                 @Column("version") String version,
                 @Column("status") Status status) {

        super(id, organizationId);
        this.type = type;
        this.displayName = displayName;
        this.websiteUrl = websiteUrl;
        this.externalUrl = externalUrl;
        this.termsAndConditionsUrl = termsAndConditionsUrl;
        this.privacyPolicyUrl = privacyPolicyUrl;
        this.imageUrl = imageUrl;
        this.fileBlobId = fileBlobId;

        final ZoneId zoneId = TimeZone.getTimeZone(timeZone).toZoneId();

        this.shortName = shortName;
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timeZone = zoneId;
        this.begin = begin.withZoneSameInstant(zoneId);
        this.end = end.withZoneSameInstant(zoneId);
        this.currency = currency;
        this.vatIncluded = vatStatus == PriceContainer.VatStatus.INCLUDED;
        this.vat = vat;
        this.privateKey = privateKey;

        this.locales = locales;
        this.allowedPaymentProxies = Arrays.stream(Optional.ofNullable(allowedPaymentProxies).orElse("").split(","))
                .filter(StringUtils::isNotBlank)
                .map(PaymentProxy::valueOf)
                .collect(Collectors.toList());
        this.vatStatus = vatStatus;
        this.srcPriceCts = srcPriceInCents;
        this.version = version;
        this.status = status;
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

    public boolean isInternal() {
        return type == EventType.INTERNAL;
    }

    public boolean getUseFirstAndLastName() {
        return mustUseFirstAndLastName();
    }

    public boolean mustUseFirstAndLastName() {
        return mustUseFirstAndLastName(this);
    }


    private static boolean mustUseFirstAndLastName(Event event) {
        return event.getVersion() != null
            && MigrationVersion.fromVersion(event.getVersion()).compareTo(MigrationVersion.fromVersion(VERSION_FOR_FIRST_AND_LAST_NAME)) >= 0;
    }

    public boolean expired() {
        return expiredSince(0);
    }

    public boolean expiredSince(int days) {
        return ZonedDateTime.now(getZoneId()).truncatedTo(ChronoUnit.DAYS).minusDays(days).isAfter(getEnd().truncatedTo(ChronoUnit.DAYS));
    }

    public String getPrivacyPolicyLinkOrNull() {
        return StringUtils.trimToNull(privacyPolicyUrl);
    }
}
