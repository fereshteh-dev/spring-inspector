package io.github.fereshtehdev.springinspector.intellij.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.fereshtehdev.springinspector.intellij.report.ArchitectureView;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArchitectureViewTest {

    static final String REPORT = """
            {
              "rootPackage": "com.shop", "classes": 9,
              "styles": [{"style": "LAYERED", "title": "Layered", "description": "By technical layer.",
                          "confidence": "high", "evidence": ["top-level packages repository, service, web"]}],
              "parts": [{"name": "web", "classes": 3, "dependsOn": {"service": 3, "repository": 1}},
                        {"name": "service", "classes": 1, "dependsOn": {}}],
              "cycles": [["orders", "billing"]],
              "hotspots": [{"type": "com.shop.Hub", "usedBy": 12, "uses": 11}],
              "suggestions": [{"title": "Organise the code by feature", "why": "Features <span>",
                               "steps": ["  com.shop.order: Order, OrderController"]}],
              "diagram": "flowchart LR",
              "markdown": "# Architecture of `com.shop`"
            }
            """;

    @Test
    void readsTheCommandLinesArchitectureReport() {
        ArchitectureView view = ArchitectureView.read(REPORT);

        assertThat(view.rootPackage()).isEqualTo("com.shop");
        assertThat(view.classes()).isEqualTo(9);
        assertThat(view.styles()).singleElement().satisfies(style -> {
            assertThat(style.title()).isEqualTo("Layered");
            assertThat(style.evidence()).containsExactly("top-level packages repository, service, web");
        });
        assertThat(view.parts().getFirst().dependsOn()).containsEntry("service", 3).containsEntry("repository", 1);
        assertThat(view.cycles()).containsExactly(List.of("orders", "billing"));
        assertThat(view.hotspots().getFirst().usedBy()).isEqualTo(12);
        assertThat(view.markdown()).startsWith("# Architecture");
        assertThatThrownBy(() -> ArchitectureView.read("{}")).hasMessageContaining("not an architecture report");
    }

    @Test
    void showsEverySectionAndEscapesProjectText() {
        String html = ArchitectureHtml.of(ArchitectureView.read(REPORT));

        assertThat(html)
                .contains("<h2>Architecture of com.shop</h2><p>9 classes</p>")
                .contains("<b>Layered</b> (high confidence): By technical layer.")
                .contains("<b>web</b>, 3 classes, uses ")
                .contains("<b>service</b>, 1 class</li>")
                .contains("orders ⇄ billing")
                .contains("com.shop.Hub: used by 12, uses 11")
                .contains("Features &lt;span&gt;")
                .contains("<li>com.shop.order: Order, OrderController</li>")
                .doesNotContain("<span>");
    }

    @Test
    void saysWhenThereIsNothingToSuggest() {
        String html = ArchitectureHtml.of(ArchitectureView.read(
                "{\"rootPackage\": \"\", \"classes\": 0, \"styles\": [], \"parts\": [], \"cycles\": [],"
                        + " \"hotspots\": [], \"suggestions\": [], \"markdown\": \"\"}"));

        assertThat(html)
                .contains("Architecture of the project")
                .contains("No common style recognised.")
                .contains("None: the structure is consistent.");
    }
}
