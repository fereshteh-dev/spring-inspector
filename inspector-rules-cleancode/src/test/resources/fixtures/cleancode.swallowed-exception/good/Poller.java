package com.acme.jobs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

class Poller {

    void poll(Path inbox) {
        while (true) {
            try {
                Files.delete(inbox.resolve("done"));
            } catch (IOException ignored) {
                // The marker may already be gone; the next poll recreates it.
            }
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    Object result(Future<Object> future) {
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException _) {
            return null;
        }
    }

    void cleanup(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // Best effort: a leftover temporary file is harmless.
        }
    }
}
