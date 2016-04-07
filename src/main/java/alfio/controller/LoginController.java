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

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Controller
public class LoginController {

    private static final String REDIRECT_ADMIN = "redirect:/admin/";

    @RequestMapping(value="/authentication", method = RequestMethod.GET)
    public String getLoginPage(@RequestParam(value="failed", required = false) String failed, Model model, Principal principal) {
        if(principal != null) {
            return REDIRECT_ADMIN;
        }
        model.addAttribute("failed", failed != null);
        return "/login/login";
    }

    @RequestMapping(value="/authenticate", method = RequestMethod.POST)
    public String doLogin() {
        return REDIRECT_ADMIN;
    }

}
