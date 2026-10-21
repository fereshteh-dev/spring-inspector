package com.acme.orders;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

class SafeOrderQueries {

    private static final String TABLE = "orders";

    private final JdbcTemplate jdbc;
    private final AuditLog audit;

    SafeOrderQueries(JdbcTemplate jdbc, AuditLog audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    List<String> byCustomer(String customer) {
        return jdbc.queryForList("select id from orders where customer = ?", String.class, customer);
    }

    List<String> all() {
        return jdbc.queryForList("select id from " + TABLE + " order by id", String.class);
    }

    void record(String orderId) {
        audit.update("order " + orderId + " was read");
        audit.execute("select-from-where audit trail for " + orderId);
    }
}
