package com.acme;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

class Login {
    String view(@RequestParam String next) {
        return "redirect:" + next;
    }

    void servlet(HttpServletResponse response, @RequestParam String next) throws Exception {
        response.sendRedirect(next);
    }

    RedirectView object(@RequestParam String next) {
        return new RedirectView(next);
    }

    void slashOnly(HttpServletResponse response, @RequestParam String next) throws Exception {
        response.sendRedirect("/" + next);
    }
}
