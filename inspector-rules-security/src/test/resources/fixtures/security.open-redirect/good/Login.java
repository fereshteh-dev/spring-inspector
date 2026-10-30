package com.acme;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

class Login {
    String home() {
        return "redirect:/home";
    }

    String order(@PathVariable String id) {
        return "redirect:/orders/" + id;
    }

    String numeric(@PathVariable long id) {
        return "redirect:" + id;
    }

    void fixedHost(HttpServletResponse response, @RequestParam String q) throws Exception {
        response.sendRedirect("https://shop.example.com/search?q=" + q);
    }
}
