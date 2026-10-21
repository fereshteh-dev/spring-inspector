package com.acme.rates;

import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class RateCache {

    private final RestClient rates = RestClient.create("https://rates.example.com");
    private final ReentrantLock lock = new ReentrantLock();
    private String latest;

    String refresh() {
        lock.lock();
        try {
            latest = rates.get().retrieve().body(String.class);
            return latest;
        } finally {
            lock.unlock();
        }
    }

    synchronized String current() {
        return latest;
    }
}
