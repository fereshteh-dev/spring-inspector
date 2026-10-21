package com.acme.assistant;

import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.core.JdbcTemplate;

class ReportAssistant {

    private final ChatClient chat;
    private final JdbcTemplate jdbc;

    ReportAssistant(ChatClient chat, JdbcTemplate jdbc) {
        this.chat = chat;
        this.jdbc = jdbc;
    }

    List<Map<String, Object>> ordersFor(String question) {
        ReportFilter filter = chat.prompt().user(question).call().entity(ReportFilter.class);
        String status = filter.status().equals("OPEN") ? "OPEN" : "CLOSED";
        return jdbc.queryForList("select * from orders where status = ?", status);
    }

    String summary(String question) {
        return chat.prompt().user(question).call().content();
    }

    record ReportFilter(String status) {}
}
