package com.acme.clients;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

class Clients {

    HttpClient jdk() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    RestClient rest(SimpleClientHttpRequestFactory factory) {
        return RestClient.builder().requestFactory(factory).build();
    }

    RestClient fromBoot(RestClient.Builder builder) {
        return builder.baseUrl("https://api.example.com").build();
    }

    RestClient fromTemplate(RestTemplate template) {
        return RestClient.create(template);
    }
}
