package io.github.fereshtehdev.springinspector.core.domain.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit.Import;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class JavaUnitTest {

    @Test
    void readsPackageImportsAndTheAnnotationsOfTheMainType() {
        JavaUnit unit = JavaUnit.parse(Path.of("src/main/java/com/acme/web/OrderController.java"), """
                package com.acme.web;

                import com.acme.service.OrderService;
                import com.acme.domain.*;
                import static org.assertj.core.api.Assertions.assertThat;
                // import com.acme.Commented;
                /* import com.acme.AlsoCommented; */

                /** Orders, see {@link com.acme.Other}. @Deprecated in the docs does not count. */
                @RestController
                @RequestMapping("/orders")
                class OrderController {
                    @Autowired OrderService service;
                }
                """);

        assertThat(unit.packageName()).isEqualTo("com.acme.web");
        assertThat(unit.typeName()).isEqualTo("OrderController");
        assertThat(unit.qualifiedName()).isEqualTo("com.acme.web.OrderController");
        assertThat(unit.imports())
                .containsExactly(
                        new Import("com.acme.service.OrderService", false, 3), new Import("com.acme.domain", true, 4));
        assertThat(unit.stereotypes()).containsExactlyInAnyOrder("RestController", "RequestMapping");
    }

    @Test
    void findsImportsDirectlyOrThroughAWildcard() {
        JavaUnit unit = JavaUnit.parse(
                Path.of("A.java"), "import com.acme.repo.Orders;\nimport com.acme.domain.*;\nclass A {}\n");

        assertThat(unit.importOf("com.acme.repo.Orders"))
                .hasValueSatisfying(found -> assertThat(found.line()).isEqualTo(1));
        assertThat(unit.importOf("com.acme.domain.Order"))
                .hasValueSatisfying(found -> assertThat(found.wildcard()).isTrue());
        assertThat(unit.importOf("com.acme.other.Thing")).isEmpty();
        assertThat(unit.packageName()).isEmpty();
        assertThat(unit.qualifiedName()).isEqualTo("A");
    }

    @Test
    void indexesTheProjectsOwnTypes() {
        JavaUnit order = JavaUnit.parse(Path.of("Order.java"), "package com.acme;\nclass Order {}\n");
        ImportGraph graph = new ImportGraph(List.of(order));

        assertThat(graph.byQualifiedName()).containsKey("com.acme.Order");
        assertThat(graph.unit("com.acme.Order")).contains(order);
        assertThat(graph.unit("com.acme.Missing")).isEmpty();
        assertThat(ImportGraph.empty().units()).isEmpty();
    }
}
