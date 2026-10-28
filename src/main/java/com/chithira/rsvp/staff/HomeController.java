package com.chithira.rsvp.staff;

import com.chithira.rsvp.event.CardTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The print shop's public homepage, and the staff portal login beneath it. */
@Controller
public class HomeController {

    @GetMapping("/")
    String home(Model model) {
        model.addAttribute("templates", CardTemplate.values());
        return "site/home";
    }

    @GetMapping("/portal/login")
    String login() {
        return "portal/login";
    }
}
