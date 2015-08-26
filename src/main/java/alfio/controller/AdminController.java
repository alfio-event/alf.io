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
package alfio.controller;

import alfio.manager.EventManager;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import com.opencsv.CSVWriter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.Principal;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final String[] CSV_FULL_HEADER = new String[]{"ID", "creation", "category", "event", "status", "originalPrice", "paidPrice","reservationID", "Name", "E-Mail", "locked", "job title", "company", "Phone Number", "address", "country", "T-Shirt size", "gender", "Notes", "Language"};
    private static final String[] CSV_TICKETS_HEADER = new String[]{"ID", "Name", "Company"};

    private static final int[] BOM_MARKERS = new int[] {0xEF, 0xBB, 0xBF};
    private final EventManager eventManager;
    private final ConfigurationManager configurationManager;

    @Autowired
    public AdminController(EventManager eventManager, ConfigurationManager configurationManager) {
        this.eventManager = eventManager;
        this.configurationManager = configurationManager;
    }

    //catch both "/admin" and "/admin/"
    @RequestMapping("")
    public String adminHome(Model model, @Value("${alfio.version}") String version, Principal principal) {
        model.addAttribute("alfioVersion", version);
        model.addAttribute("username", principal.getName());
        model.addAttribute("basicConfigurationNeeded", configurationManager.isBasicConfigurationNeeded());
        return "/admin/index";
    }

    @RequestMapping("/events/{eventName}/export/all-tickets.csv")
    public void downloadAllTicketsCSV(@PathVariable("eventName") String eventName, Principal principal, HttpServletResponse response) throws IOException {
        Event event = Optional.ofNullable(eventManager.getSingleEvent(eventName, principal.getName())).orElseThrow(IllegalArgumentException::new);
        Map<Integer, TicketCategory> categoriesMap = eventManager.loadTicketCategories(event).stream().collect(Collectors.toMap(TicketCategory::getId, Function.identity()));
        ZoneId eventZoneId = event.getZoneId();
        downloadTicketsCSV(eventName, "all-tickets", CSV_FULL_HEADER, principal, response, eventManager,
            t -> new String[]{
                t.getUuid(), t.getCreation().withZoneSameInstant(eventZoneId).toString(), categoriesMap.get(t.getCategoryId()).getName(), eventName, t.getStatus().toString(),
                t.getOriginalPrice().toString(), t.getPaidPrice().toString(), t.getTicketsReservationId(), t.getFullName(), t.getEmail(), String.valueOf(t.getLockedAssignment()),
                t.getJobTitle(), t.getCompany(), t.getPhoneNumber(), t.getAddress(), t.getCountry(), t.getTshirtSize(), t.getGender(), t.getNotes(), t.getUserLanguage()
            });
    }

    @RequestMapping("/events/{eventName}/export/badges.csv")
    public void downloadBadgesCSV(@PathVariable("eventName") String eventName, Principal principal, HttpServletResponse response) throws IOException {
        downloadTicketsCSV(eventName, "badges", CSV_TICKETS_HEADER, principal, response, eventManager, t -> new String[]{t.getUuid(), t.getFullName(), StringUtils.defaultString(t.getCompany())});
    }

    private static void downloadTicketsCSV(String eventName, String fileName, String[] header,
                                           Principal principal, HttpServletResponse response,
                                           EventManager eventManager, Function<Ticket, String[]> ticketMapper) throws IOException {
        Validate.isTrue(StringUtils.isNotBlank(eventName), "Event name is not valid");
        List<Ticket> tickets = eventManager.findAllConfirmedTickets(eventName, principal.getName());
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + eventName + "-"+fileName+".csv");
        try(ServletOutputStream out = response.getOutputStream()) {
            for (int marker : BOM_MARKERS) {//UGLY-MODE_ON: specify that the file is written in UTF-8 with BOM, thanks to alexr http://stackoverflow.com/a/4192897
                out.write(marker);
            }
            CSVWriter writer = new CSVWriter(new OutputStreamWriter(out));
            writer.writeNext(header);
            tickets.stream()
                    .map(ticketMapper)
                    .forEach(writer::writeNext);
            writer.flush();
            out.flush();
        }
    }

}
