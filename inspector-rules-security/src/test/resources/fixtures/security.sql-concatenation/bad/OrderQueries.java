package com.acme.orders;

import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

class OrderQueries {

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final Connection connection;

    OrderQueries(JdbcTemplate jdbc, EntityManager entityManager, Connection connection) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.connection = connection;
    }

    List<String> byCustomer(String customer) {
        return jdbc.queryForList("select id from orders where customer = '" + customer + "'", String.class);
    }

    List<?> byStatus(String status) {
        String jpql = "select o from Order o where o.status = '%s'".formatted(status);
        return entityManager.createQuery(jpql).getResultList();
    }

    ResultSet byId(String id) throws SQLException {
        Statement statement = connection.createStatement();
        return statement.executeQuery("SELECT * FROM orders WHERE id = " + id);
    }
}
