package com.acme.profiles;

import java.util.Optional;

class Profile {

    private String nickname;

    Optional<String> nickname() {
        return Optional.ofNullable(nickname);
    }

    void rename(String newName) {
        nickname = newName;
    }

    private static String orDefault(Optional<String> value) {
        return value.orElse("anonymous");
    }
}
