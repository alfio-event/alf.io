package io.bagarino.model;

import io.bagarino.model.user.Organization;
import lombok.Data;

import java.util.Collection;

@Data
public class Event {
    private final String description;
    private final Collection<TicketCategory> ticketCategories;
    private final Organization owner;
    private final Collection<Section> sections;
}
