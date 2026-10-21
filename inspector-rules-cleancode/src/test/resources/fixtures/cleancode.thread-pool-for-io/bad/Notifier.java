package com.acme.notify;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class Notifier {

    private final ExecutorService senders = Executors.newFixedThreadPool(50);
}
