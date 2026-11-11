package io.github.fereshtehdev.springinspector.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.cli.CliRun.Result;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentCommandTest {

    @TempDir
    Path temp;

    CliRun cli;

    @BeforeEach
    void createProject() {
        cli = CliRun.inNewRepository(temp);
        cli.write("src/main/resources/application.properties", "spring.application.name=shop\n");
        cli.write("src/main/java/com/shop/web/OrderController.java", """
                package com.shop.web;

                import com.shop.service.OrderService;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class OrderController {

                    @Autowired
                    OrderService orders;

                    @GetMapping("/orders")
                    String all() {
                        return "";
                    }
                }
                """);
        cli.write("src/main/java/com/shop/service/OrderService.java", """
                package com.shop.service;

                import org.springframework.stereotype.Service;

                @Service
                public class OrderService {}
                """);
        cli.commit("shop");
    }

    @AfterEach
    void close() {
        cli.close();
    }

    @Test
    void writesAPdfIntoTheProjectByDefault() throws IOException {
        Result result = cli.run("document");

        Path pdf = cli.project().resolve("spring-inspector-document.pdf");
        assertThat(result.exitCode()).isEqualTo(ExitCodes.OK);
        assertThat(result.out().strip()).isEqualTo("Wrote " + pdf.toRealPath());
        assertThat(new String(Files.readAllBytes(pdf), StandardCharsets.ISO_8859_1))
                .startsWith("%PDF-");
    }

    @Test
    void describesTheProjectAndItsFindingsInMarkdown() throws IOException {
        Result result = cli.run("document", "--format", "markdown");

        String markdown = Files.readString(cli.project().resolve("spring-inspector-document.md"));
        assertThat(result.exitCode()).isEqualTo(ExitCodes.OK);
        assertThat(markdown)
                .startsWith("# shop: project document")
                .contains("shop is a Spring Boot 4.1.1 application on Java 25, with 2 classes.")
                .contains("| GET | `/orders` | `OrderController.all` |")
                .contains("| `OrderController` | HTTP | `OrderService` |")
                .contains("No Spring Security configuration was found")
                .contains("Code quality and vulnerabilities")
                .contains("spring.field-injection at src/main/java/com/shop/web/OrderController.java");
    }

    @Test
    void writesHtmlToTheFileItIsGiven() throws IOException {
        Result result = cli.run("document", "--format", "html", "--output", "docs/design/shop.html");

        Path html = cli.project().resolve("docs/design/shop.html");
        assertThat(result.exitCode()).isEqualTo(ExitCodes.OK);
        assertThat(Files.readString(html)).startsWith("<!DOCTYPE html>").contains("<h1>shop: project document</h1>");
    }

    @Test
    void leavesOutTheQualityChapterWithoutInspecting() throws IOException {
        Result result = cli.run("document", "--format", "markdown", "--no-findings");

        String markdown = Files.readString(cli.project().resolve("spring-inspector-document.md"));
        assertThat(result.exitCode()).isEqualTo(ExitCodes.OK);
        assertThat(markdown).contains("| GET | `/orders` |").doesNotContain("Code quality");
        assertThat(cli.project().resolve(".spring-inspector")).doesNotExist();
    }

    @Test
    void namesTheProjectAfterItsFolderWhenItHasNoApplicationName() throws IOException {
        cli.write("src/main/resources/application.properties", "server.port=8080\n");

        cli.run("document", "--format", "markdown", "--no-findings");

        assertThat(Files.readString(cli.project().resolve("spring-inspector-document.md")))
                .startsWith("# project: project document");
    }

    @Test
    void refusesAProjectDirectoryThatDoesNotExist() {
        Result result = cli.run("document", "--project", "missing");

        assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
        assertThat(result.err()).contains("Not a directory");
    }
}
