package com.acme.files;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DownloadController {

    private final Path base = Path.of("/srv/reports");

    @GetMapping("/reports/{name}")
    byte[] report(@PathVariable String name) throws Exception {
        Path file = base.resolve(name).normalize();
        if (!file.startsWith(base)) {
            throw new IllegalArgumentException("Not a report: " + name);
        }
        return Files.readAllBytes(file);
    }

    byte[] internal(String name) throws Exception {
        return Files.readAllBytes(base.resolve(name));
    }
}
