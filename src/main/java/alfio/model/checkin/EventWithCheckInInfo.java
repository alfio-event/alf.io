package alfio.model.checkin;

import alfio.model.Event;
import alfio.model.EventAndOrganizationId;
import alfio.model.EventCheckInInfo;
import alfio.model.EventHiddenFieldContainer;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.support.JSONData;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;

@Getter
public class EventWithCheckInInfo extends EventAndOrganizationId implements EventHiddenFieldContainer, EventCheckInInfo {

    private final Event.EventFormat format;
    private final String shortName;
    private final String displayName;
    private final ZonedDateTime begin;
    private final ZonedDateTime end;
    private final ZoneId zoneId;
    private final String privateKey;
    private final AlfioMetadata metadata;


    public EventWithCheckInInfo(@Column("id") int id,
                                @Column("format") Event.EventFormat format,
                                @Column("short_name") String shortName,
                                @Column("display_name") String displayName,
                                @Column("start_ts") ZonedDateTime startTs,
                                @Column("end_ts") ZonedDateTime endTs,
                                @Column("time_zone") String timezone,
                                @Column("private_key") String privateKey,
                                @Column("org_id") int organizationId,
                                @Column("metadata") @JSONData AlfioMetadata metadata) {
        super(id, organizationId);
        this.zoneId = ZoneId.of(timezone);
        this.format = format;
        this.shortName = shortName;
        this.displayName = displayName;
        this.begin = startTs.withZoneSameInstant(zoneId);
        this.end = endTs.withZoneSameInstant(zoneId);
        this.privateKey = privateKey;
        this.metadata = metadata;
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
}