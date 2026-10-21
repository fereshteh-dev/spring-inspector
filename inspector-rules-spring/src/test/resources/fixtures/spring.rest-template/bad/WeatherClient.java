package com.acme.weather;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
class WeatherClient {

    private final RestTemplate restTemplate;

    WeatherClient() {
        this.restTemplate = new RestTemplate();
    }
}
