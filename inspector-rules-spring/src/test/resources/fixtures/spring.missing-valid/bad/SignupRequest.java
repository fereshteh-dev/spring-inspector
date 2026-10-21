package com.acme.signup;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

record SignupRequest(@NotBlank @Email String email, @NotBlank String name) {}
