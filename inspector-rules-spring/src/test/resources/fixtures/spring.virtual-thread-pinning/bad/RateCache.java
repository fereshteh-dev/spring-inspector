package com.acme.rates;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class RateCache {

    private final RestClient rates = RestClient.create("https://rates.example.com");
    private String latest;

    synchronized String refresh() {
        latest = rates.get().retrieve().body(String.class);
        return latest;
    }

    String waitForRate() throws InterruptedException {
        synchronized (this) {
            Thread.sleep(50);
            return latest;
        }
    }
}
