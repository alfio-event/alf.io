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
package alfio.model.checkin;

import alfio.model.*;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.support.JSONData;
import alfio.util.EventUtil;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;

@Getter
public class EventWithCheckInInfo extends EventAndOrganizationId implements EventHiddenFieldContainer, EventCheckInInfo, LocalizedContent {

    private final Event.EventFormat format;
    private final String shortName;
    private final String displayName;
    private final ZonedDateTime begin;
    private final ZonedDateTime end;
    private final ZoneId zoneId;
    private final String privateKey;
    private final AlfioMetadata metadata;
    private final List<ContentLanguage> contentLanguages;
    private final String version;


    public EventWithCheckInInfo(@Column("id") int id,
                                @Column("format") Event.EventFormat format,
                                @Column("short_name") String shortName,
                                @Column("display_name") String displayName,
                                @Column("start_ts") ZonedDateTime startTs,
                                @Column("end_ts") ZonedDateTime endTs,
                                @Column("time_zone") String timezone,
                                @Column("private_key") String privateKey,
                                @Column("org_id") int organizationId,
                                @Column("metadata") @JSONData AlfioMetadata metadata,
                                @Column("locales") int locales,
                                @Column("version") String version) {
        super(id, organizationId);
        this.zoneId = ZoneId.of(timezone);
        this.format = format;
        this.shortName = shortName;
        this.displayName = displayName;
        this.begin = startTs.withZoneSameInstant(zoneId);
        this.end = endTs.withZoneSameInstant(zoneId);
        this.privateKey = privateKey;
        this.metadata = metadata;
        this.contentLanguages = ContentLanguage.findAllFor(locales);
        this.version = version;
    }

    public boolean isOnline() {
        return format == Event.EventFormat.ONLINE;
    }

    @Override
    public String getPrivateKey() {
        return privateKey;
    }

    @Override
    public Pair<String, String> getLatLong() {
        return Pair.of(null, null);
    }

    @Override
    public ZoneId getZoneId() {
        return zoneId;
    }

    @Override
    public BigDecimal getVat() {
        return null;
    }

    @Override
    public List<ContentLanguage> getContentLanguages() {
        return contentLanguages;
    }

    @Override
    public boolean supportsQRCodeCaseInsensitive() {
        return EventUtil.supportsCaseInsensitiveQRCode(version);
    }

    @Override
    public boolean supportsLinkedAdditionalServices() {
        return EventUtil.supportsLinkedAdditionalServices(version);
    }
}
