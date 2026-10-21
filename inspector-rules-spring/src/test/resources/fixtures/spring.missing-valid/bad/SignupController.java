package com.acme.signup;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SignupController {

    @PostMapping("/signup")
    void signup(@RequestBody SignupRequest request) {}
}
