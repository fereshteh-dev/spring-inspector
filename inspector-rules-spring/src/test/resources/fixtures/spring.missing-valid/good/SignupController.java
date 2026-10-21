package com.acme.signup;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SignupController {

    @PostMapping("/signup")
    void signup(@Valid @RequestBody SignupRequest request) {}

    @PostMapping("/notes")
    void note(@RequestBody Note note) {}

    record Note(String text) {}
}
