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

import lombok.AllArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
@AllArgsConstructor
public class IndexController {

    @RequestMapping(value = "/", method = RequestMethod.HEAD)
    public ResponseEntity<String> replyToProxy() {
        return ResponseEntity.ok("Up and running!");
    }

    @RequestMapping(value = "/healthz", method = RequestMethod.GET)
    public ResponseEntity<String> replyToK8s() {
        return ResponseEntity.ok("Up and running!");
    }


    //url defined in the angular app in app-routing.module.ts
    /**
    <pre>
    { path: '', component: EventListComponent, canActivate: [LanguageGuard] },
    { path: 'event/:eventShortName', component: EventDisplayComponent, canActivate: [LanguageGuard] },
    { path: 'event/:eventShortName/reservation/:reservationId', component: ReservationComponent, canActivate: [LanguageGuard, ReservationGuard], children: [
        { path: 'book', component: BookingComponent, canActivate: [ReservationGuard] },
        { path: 'overview', component: OverviewComponent, canActivate: [ReservationGuard] },
        { path: 'waitingPayment', redirectTo: 'waiting-payment'},
        { path: 'waiting-payment', component: OfflinePaymentComponent, canActivate: [ReservationGuard] },
        { path: 'processing-payment', component: ProcessingPaymentComponent, canActivate: [ReservationGuard] },
        { path: 'success', component: SuccessComponent, canActivate: [ReservationGuard]}
    ]},
    { path: 'event/:eventShortName/ticket/:ticketId/view', component: ViewTicketComponent, canActivate: [LanguageGuard] }
    </pre>

     */
    @RequestMapping(value =  {
        "/",
        "/event/{eventShortName}",
        "/event/{eventShortName}/reservation/{reservationId}",
        "/event/{eventShortName}/reservation/{reservationId}/book",
        "/event/{eventShortName}/reservation/{reservationId}/overview",
        "/event/{eventShortName}/reservation/{reservationId}/waitingPayment",
        "/event/{eventShortName}/reservation/{reservationId}/waiting-payment",
        "/event/{eventShortName}/reservation/{reservationId}/processing-payment",
        "/event/{eventShortName}/reservation/{reservationId}/success",
        "/event/{eventShortName}/ticket/{ticketId}/view"
    }, method = RequestMethod.GET)
    public void replyToIndex(HttpServletResponse response) throws IOException {
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        try (var is = new ClassPathResource("alfio-public-frontend-index.html").getInputStream(); var os = response.getOutputStream()) {
            is.transferTo(os);
        }
    }
}
