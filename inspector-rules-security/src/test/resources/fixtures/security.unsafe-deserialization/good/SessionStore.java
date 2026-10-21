package com.acme.sessions;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;

class SessionStore {

    Object read(InputStream in) throws Exception {
        try (ObjectInputStream objects = new ObjectInputStream(in)) {
            objects.setObjectInputFilter(ObjectInputFilter.Config.createFilter("com.acme.sessions.*;!*"));
            return objects.readObject();
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    @JsonSubTypes({@JsonSubTypes.Type(value = Created.class, name = "created")})
    interface Event {}

    record Created(String id) implements Event {}
}
