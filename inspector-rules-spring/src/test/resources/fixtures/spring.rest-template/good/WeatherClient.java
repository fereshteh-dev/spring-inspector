package com.acme.weather;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class WeatherClient {

    private final RestClient restClient;

    WeatherClient(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("https://weather.example.com").build();
    }
}
