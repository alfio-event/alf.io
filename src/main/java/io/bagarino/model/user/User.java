package io.bagarino.model.user;

import io.bagarino.model.Ticket;
import lombok.Data;

import java.util.Collection;

@Data
public class User {
    private final Collection<Organization> organizations;
    private final Collection<Ticket> tickets;
    private final String username;//the password won't be mapped for security reasons

}
