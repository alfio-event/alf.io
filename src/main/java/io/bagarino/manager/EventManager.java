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
package io.bagarino.manager;

import io.bagarino.manager.user.UserManager;
import io.bagarino.model.Event;
import io.bagarino.repository.EventRepository;
import io.bagarino.repository.join.EventOrganizationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class EventManager {

    private final UserManager userManager;
    private final EventOrganizationRepository eventOrganizationRepository;
    private final EventRepository eventRepository;

    @Autowired
    public EventManager(UserManager userManager,
                        EventOrganizationRepository eventOrganizationRepository,
                        EventRepository eventRepository) {
        this.userManager = userManager;
        this.eventOrganizationRepository = eventOrganizationRepository;
        this.eventRepository = eventRepository;
    }

    public List<Event> getAllEvents(String username) {
        return userManager.findUserOrganizations(username)
                    .parallelStream()
                    .flatMap(o -> eventOrganizationRepository.findByOrganizationId(o.getId()).stream())
                    .map(eo -> eventRepository.findById(eo.getEventId()))
                    .collect(Collectors.toList());
    }
}
