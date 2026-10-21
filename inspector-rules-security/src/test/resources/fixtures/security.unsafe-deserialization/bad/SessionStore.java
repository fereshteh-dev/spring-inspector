package com.acme.sessions;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.io.ObjectInputStream;

class SessionStore {

    Object read(InputStream in) throws Exception {
        try (ObjectInputStream objects = new ObjectInputStream(in)) {
            return objects.readObject();
        }
    }

    ObjectMapper mapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(mapper.getPolymorphicTypeValidator());
        return mapper;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    interface Event {}
}
