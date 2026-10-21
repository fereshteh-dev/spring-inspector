package com.acme.files;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DownloadController {

    private final Path base = Path.of("/srv/reports");

    @GetMapping("/reports/{name}")
    byte[] report(@PathVariable String name) throws Exception {
        return Files.readAllBytes(base.resolve(name));
    }

    @GetMapping("/legacy")
    long legacy(@RequestParam String file) {
        String location = "/srv/legacy/" + file;
        return new File(location).length();
    }
}
