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
package alfio.controller.api.support;

import alfio.model.Event;
import alfio.model.EventDescription;
import alfio.model.TicketCategory;
import alfio.model.TicketCategoryDescription;
import alfio.repository.EventDescriptionRepository;
import alfio.repository.TicketCategoryDescriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class DescriptionsLoader {

    private final EventDescriptionRepository eventDescriptionRepository;
    private final TicketCategoryDescriptionRepository categoryDescriptionRepository;

    @Autowired
    public DescriptionsLoader(EventDescriptionRepository eventDescriptionRepository, TicketCategoryDescriptionRepository categoryDescriptionRepository) {
        this.eventDescriptionRepository = eventDescriptionRepository;
        this.categoryDescriptionRepository = categoryDescriptionRepository;
    }

    public DataLoader<Event, EventDescription> eventDescriptions() {
        return e -> eventDescriptionRepository.findByEventId(e.getId());
    }

    public DataLoader<TicketCategory, TicketCategoryDescription> ticketCategoryDescriptions() {
        return c -> categoryDescriptionRepository.findByTicketCategoryId(c.getId());
    }
}
