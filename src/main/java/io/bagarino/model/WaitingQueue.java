package io.bagarino.model;

import io.bagarino.model.user.User;
import lombok.Data;

import java.util.Collection;

@Data
public class WaitingQueue {
    private final Event event; //TODO should it be directly linked with the section?
    private final Collection<User> users;

    public void addUser(User user) {
        this.users.add(user);
    }
}
