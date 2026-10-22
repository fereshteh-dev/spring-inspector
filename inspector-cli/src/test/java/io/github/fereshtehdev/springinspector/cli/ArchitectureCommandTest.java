package io.github.fereshtehdev.springinspector.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.cli.CliRun.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ArchitectureCommandTest {

    @TempDir
    java.nio.file.Path temp;

    CliRun cli;

    @BeforeEach
    void createProject() {
        cli = CliRun.inNewRepository(temp);
        for (String name : new String[] {"Order", "Customer", "Product"}) {
            cli.write("src/main/java/com/shop/web/" + name + "Controller.java", """
                    package com.shop.web;

                    import com.shop.service.%1$sService;
                    import com.shop.repository.%1$sRepository;

                    @RestController
                    class %1$sController {}
                    """.formatted(name));
            cli.write("src/main/java/com/shop/service/" + name + "Service.java", """
                    package com.shop.service;

                    import com.shop.repository.%1$sRepository;

                    @Service
                    public class %1$sService {}
                    """.formatted(name));
            cli.write("src/main/java/com/shop/repository/" + name + "Repository.java", """
                    package com.shop.repository;

                    public interface %sRepository {}
                    """.formatted(name));
        }
    }

    @AfterEach
    void close() {
        cli.close();
    }

    @Test
    void describesTheStructureAndSuggestsACleanerOne() {
        Result result = cli.run("architecture");

        assertThat(result.exitCode()).isEqualTo(ExitCodes.OK);
        assertThat(result.out())
                .contains("Architecture of com.shop (9 classes)")
                .contains("+ Layered (high): top-level packages repository, service, web")
                .contains("web (3 classes) uses repository (3), service (3)")
                .contains("1. Organise the code by feature")
                .contains("Route data access through services")
                .contains("OrderController -> OrderRepository");
    }

    @Test
    void writesMarkdownWithADiagram() {
        Result result = cli.run("architecture", "--format", "markdown");

        assertThat(result.out())
                .startsWith("# Architecture of `com.shop`")
                .contains("| Layered | high |")
                .contains("```mermaid\nflowchart LR")
                .contains("### Organise the code by feature")
                .contains("  - com.shop.order: OrderController, OrderRepository, OrderService");
    }

    @Test
    void writesJsonForTheIde() throws Exception {
        Result result = cli.run("architecture", "--format", "json");

        JsonNode report = JsonMapper.builder().build().readTree(result.out());
        assertThat(report.path("rootPackage").asString()).isEqualTo("com.shop");
        assertThat(report.path("classes").asInt()).isEqualTo(9);
        assertThat(report.path("styles").get(0).path("style").asString()).isEqualTo("LAYERED");
        assertThat(report.path("parts")).hasSize(3);
        assertThat(report.path("suggestions").get(0).path("steps").size()).isGreaterThan(2);
        assertThat(report.path("diagram").asString()).startsWith("flowchart LR");
        assertThat(report.path("markdown").asString()).startsWith("# Architecture");
    }

    @Test
    void reportsAProjectWithoutJava() {
        Result result =
                cli.run("architecture", "--project", temp.resolve("home").toString());

        assertThat(result.out()).contains("none recognised").contains("None: the structure is consistent.");
    }
}
