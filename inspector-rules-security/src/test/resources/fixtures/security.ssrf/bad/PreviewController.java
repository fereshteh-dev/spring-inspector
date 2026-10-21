package com.acme.preview;

import java.net.URI;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

@RestController
class PreviewController {

    private final RestTemplate restTemplate = new RestTemplate();
    private final RestClient restClient = RestClient.create();

    @GetMapping("/preview")
    String preview(@RequestParam String url) {
        return restTemplate.getForObject(url, String.class);
    }

    @GetMapping("/proxy")
    String proxy(@RequestParam String host, @RequestParam String path) {
        return restClient.get().uri("https://" + host + "/" + path).retrieve().body(String.class);
    }

    @GetMapping("/fetch")
    String fetch(@RequestParam String target) {
        URI uri = URI.create(target);
        return restClient.get().uri(uri).retrieve().body(String.class);
    }
}
