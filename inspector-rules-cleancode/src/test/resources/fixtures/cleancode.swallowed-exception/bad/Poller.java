package com.acme.jobs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class Poller {

    void poll(Path inbox) {
        while (true) {
            try {
                Files.delete(inbox.resolve("done"));
            } catch (IOException e) {
            }
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {}
        }
    }
}
