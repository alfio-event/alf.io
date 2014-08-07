package io.bagarino.model;

import io.bagarino.model.user.Organization;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

@Data
public class TicketCategory {
    private final LocalDateTime inception;
    private final LocalDateTime end;
    private final Collection<Organization> qualifyingOrganizations;
    private final Optional<Discount> appliedDiscount;
    private final int maxTickets;

    public void addQualifyingOrganization(Organization organization) {
        qualifyingOrganizations.add(organization);
    }
}
