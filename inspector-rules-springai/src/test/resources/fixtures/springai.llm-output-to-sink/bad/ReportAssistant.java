package com.acme.assistant;

import java.nio.file.Files;
import java.nio.file.Path;
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

    List<Map<String, Object>> answer(String question) {
        String sql = chat.prompt().user("Write SQL for: " + question).call().content();
        return jdbc.queryForList(sql);
    }

    void cleanUp(String request) throws Exception {
        String command = chat.prompt().user(request).call().content();
        Runtime.getRuntime().exec(new String[] {"sh", "-c", command});
        new ProcessBuilder("sh", "-c", "echo " + command).start();
    }

    byte[] attachment(String request) throws Exception {
        return Files.readAllBytes(Path.of("/srv/files", chat.prompt().user(request).call().content()));
    }
}
