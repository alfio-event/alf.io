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
import alfio.model.Ticket;
import com.opencsv.CSVWriter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final String[] CSV_HEADER = new String[]{"ID", "Name", "E-Mail", "Phone Number", "T-Shirt size", "Notes", "Language"};
    private static final int[] BOM_MARKERS = new int[] {0xEF, 0xBB, 0xBF};
    private final EventManager eventManager;

    @Autowired
    public AdminController(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    //catch both "/admin" and "/admin/"
    @RequestMapping("")
    public String adminHome() {
        return "/admin/index";
    }

    @RequestMapping("/events/{eventName}/export-attendees.csv")
    public void downloadAttendeesCSV(@PathVariable("eventName") String eventName, Principal principal, HttpServletResponse response) throws IOException {
        Validate.isTrue(StringUtils.isNotBlank(eventName), "Event name is not valid");
        List<Ticket> tickets = eventManager.findAllConfirmedTickets(eventName, principal.getName());
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + eventName + "-attendees.csv");
        try(ServletOutputStream out = response.getOutputStream()) {
            for (int marker : BOM_MARKERS) {//UGLY-MODE_ON: specify that the file is written in UTF-8 with BOM, thanks to alexr http://stackoverflow.com/a/4192897
                out.write(marker);
            }
            CSVWriter writer = new CSVWriter(new OutputStreamWriter(out));
            writer.writeNext(CSV_HEADER);
            tickets.stream()
                    .map(t -> new String[]{t.getUuid(), t.getFullName(), t.getEmail(), t.getPhoneNumber(), t.getTshirtSize(), t.getNotes(), t.getUserLanguage()})
                    .forEach(writer::writeNext);
            writer.flush();
            out.flush();
        }
    }
}
