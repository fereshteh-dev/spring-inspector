package com.acme;

import org.springframework.web.bind.annotation.*;

class Tools {
    void fixed() throws Exception {
        new ProcessBuilder("uptime").start();
    }

    void numeric(@RequestParam int count) throws Exception {
        Runtime.getRuntime().exec("ping -c " + count + " localhost");
    }

    void notARuntime(Executor executor, @RequestParam String task) {
        executor.exec(task);
    }

    interface Executor {
        void exec(String task);
    }
}
