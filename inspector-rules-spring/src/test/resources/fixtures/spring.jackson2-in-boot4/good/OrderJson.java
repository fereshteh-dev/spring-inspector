package com.acme.orders;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.util.List;

class OrderJson {

    private final ObjectMapper mapper = new ObjectMapper();

    List<Order> read(String json) {
        return mapper.readValue(json, new TypeReference<List<Order>>() {});
    }

    record Order(@JsonProperty("order_id") String id) {}
}
