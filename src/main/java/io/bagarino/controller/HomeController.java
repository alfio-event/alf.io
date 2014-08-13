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
package io.bagarino.controller;

import io.bagarino.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Controller
public class HomeController {

	private final CustomerRepository customerRepository;

	@Autowired
	public HomeController(CustomerRepository customerRepository) {
		this.customerRepository = customerRepository;
	}

	@RequestMapping(value = "/hello")
	public String test(Model model) {
		model.addAttribute("users", customerRepository.findAll());
		return "/index";
	}

	@RequestMapping(value = "/register-user")
	public String registerUser() {
		customerRepository.create("user-" + UUID.randomUUID().toString(), "bla", "bla", "bla", "bla", "bla", "bla", "bla",
				"bla", "bla");
		return "redirect:/hello";
	}
}
