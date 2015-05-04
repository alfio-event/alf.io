package alfio.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ErrorsController {

    @RequestMapping("/404-not-found")
    public String notFound() {
        return "/event/404-not-found";
    }

    @RequestMapping("/500-internal-server-error")
    public String internalServerError() {
        return "/event/500-internal-server-error";
    }
}
