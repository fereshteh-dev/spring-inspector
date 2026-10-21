package com.acme.preview;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
class PreviewController {

    private final RestClient restClient = RestClient.create();

    @GetMapping("/users")
    String user(@RequestParam String id) {
        return restClient.get().uri("https://api.example.com/users/" + id).retrieve().body(String.class);
    }

    @GetMapping("/orders")
    String order(@RequestParam String id) {
        return restClient.get().uri("https://api.example.com/orders/{id}", id).retrieve().body(String.class);
    }
}
