package com.acme.profiles;

import java.util.Optional;

public class Profile {

    protected Optional<String> nickname = Optional.empty();

    public void rename(Optional<String> newName) {
        nickname = newName;
    }
}
