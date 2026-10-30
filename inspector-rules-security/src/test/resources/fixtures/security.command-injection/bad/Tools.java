package com.acme;

import org.springframework.web.bind.annotation.*;

class Tools {
    void ping(@RequestParam String host) throws Exception {
        Runtime.getRuntime().exec("ping " + host);
    }

    void dig(@RequestParam String host) throws Exception {
        new ProcessBuilder("sh", "-c", "dig " + host).start();
    }

    void listed(@RequestParam String host) throws Exception {
        String[] command = {"nslookup", host};
        Runtime.getRuntime().exec(command);
    }
}
