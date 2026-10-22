package com.acme.clients;

import java.net.http.HttpClient;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

class Clients {

    HttpClient jdk() {
        return HttpClient.newHttpClient();
    }

    HttpClient jdkBuilt() {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    RestClient rest() {
        return RestClient.create("https://api.example.com");
    }

    RestClient restBuilt() {
        return RestClient.builder().baseUrl("https://api.example.com").build();
    }

    RestTemplate template() {
        return new RestTemplate();
    }
}
