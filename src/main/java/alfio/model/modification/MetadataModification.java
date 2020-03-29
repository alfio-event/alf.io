package alfio.model.modification;

import alfio.model.metadata.AlfioMetadata;
import alfio.model.metadata.CallLink;
import alfio.model.metadata.OnlineConfiguration;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public class MetadataModification {

    private final List<CallLinkModification> callLinks;
    private final Map<String, String> requirementsDescriptions;

    @JsonCreator
    public MetadataModification(@JsonProperty("callLinks") List<CallLinkModification> callLinks,
                                @JsonProperty("requirementDescriptions") Map<String, String> requirementsDescriptions) {
        this.callLinks = callLinks;
        this.requirementsDescriptions = requirementsDescriptions;
    }

    public boolean isValid() {
        return callLinks != null && !callLinks.isEmpty() && callLinks.stream().allMatch(CallLinkModification::isValid);
    }

    public AlfioMetadata toMetadataObj() {
        return new AlfioMetadata(
            List.of(),
            new OnlineConfiguration(callLinks.stream().map(CallLinkModification::toCallLink).collect(Collectors.toList())),
            requirementsDescriptions,
            List.of()
        );
    }

    @Getter
    public static class CallLinkModification {
        private final String link;
        private final DateTimeModification validFrom;
        private final DateTimeModification validTo;


        @JsonCreator
        public CallLinkModification(@JsonProperty("link") String link,
                                    @JsonProperty("validFrom") DateTimeModification validFrom,
                                    @JsonProperty("validTo") DateTimeModification validTo) {
            this.link = link;
            this.validFrom = validFrom;
            this.validTo = validTo;
        }

        public boolean isValid() {
            return validFrom != null && validTo != null && validFrom.isBefore(validTo);
        }

        public CallLink toCallLink() {
            return new CallLink(link, validFrom.toLocalDateTime(), validTo.toLocalDateTime());
        }
    }
}
