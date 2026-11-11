package io.github.fereshtehdev.springinspector.document.render;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.document.content.Diagram;
import io.github.fereshtehdev.springinspector.document.render.DiagramLayout.Arrow;
import io.github.fereshtehdev.springinspector.document.render.DiagramLayout.Box;
import io.github.fereshtehdev.springinspector.document.render.DiagramLayout.Placed;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiagramLayoutTest {

    @Test
    void placesEachNodeToTheRightOfTheNodesThatPointAtIt() {
        Placed placed = DiagramLayout.of(Samples.diagram());

        Box web = box(placed, "web");
        Box service = box(placed, "service");
        Box repository = box(placed, "repository");
        assertThat(web.x() + web.width()).isLessThan(service.x());
        assertThat(service.x() + service.width()).isLessThan(repository.x());
        assertThat(placed.width()).isGreaterThan(repository.x() + repository.width());
        assertThat(placed.height()).isGreaterThan(service.y() + service.height());
    }

    @Test
    void drawsADependencyBackAsAnArrowPointingLeftBesideItsOpposite() {
        Placed placed = DiagramLayout.of(Samples.diagram());

        Arrow forward = placed.arrows().get(1);
        Arrow back = placed.arrows().get(2);
        assertThat(forward.x1()).isLessThan(forward.x2());
        assertThat(back.x1()).isGreaterThan(back.x2());
        assertThat(back.problem()).isTrue();
        assertThat(back.y1()).isNotEqualTo(forward.y1());
    }

    @Test
    void stacksNodesOfTheSameColumnWithoutOverlap() {
        Diagram fan = new Diagram(
                List.of(node("app"), node("database"), node("broker"), node("model")),
                List.of(edge("app", "database"), edge("app", "broker"), edge("app", "model")));

        Placed placed = DiagramLayout.of(fan);

        List<Box> column = placed.boxes().subList(1, 4);
        assertThat(column)
                .extracting(Box::x)
                .allMatch(x -> x > box(placed, "app").x());
        for (int at = 1; at < column.size(); at++) {
            assertThat(column.get(at).y())
                    .isGreaterThan(column.get(at - 1).y() + column.get(at - 1).height());
        }
        assertThat(box(placed, "app").centerY()).isEqualTo(box(placed, "broker").centerY());
    }

    @Test
    void connectsNodesOfTheSameColumnFromTopToBottom() {
        Diagram cycle = new Diagram(
                List.of(node("a"), node("b"), node("c")), List.of(edge("a", "b"), edge("a", "c"), edge("b", "c")));

        Placed placed = DiagramLayout.of(cycle);

        assertThat(box(placed, "c").x()).isGreaterThan(box(placed, "b").x());
        Diagram siblings = new Diagram(List.of(node("a"), node("b")), List.of(edge("a", "b"), edge("b", "a")));
        Placed mutual = DiagramLayout.of(siblings);
        assertThat(mutual.arrows()).hasSize(2);
    }

    @Test
    void survivesCyclesSelfReferencesAndUnknownNodes() {
        List<Diagram.Node> nodes = new ArrayList<>();
        List<Diagram.Edge> edges = new ArrayList<>();
        for (int at = 0; at < 6; at++) {
            nodes.add(node("n" + at));
            edges.add(edge("n" + at, "n" + (at + 1) % 6));
        }
        edges.add(edge("n0", "n0"));
        edges.add(edge("n0", "nowhere"));

        Placed placed = DiagramLayout.of(new Diagram(nodes, edges));

        assertThat(placed.boxes()).hasSize(6);
        assertThat(placed.arrows()).hasSize(6);
    }

    @Test
    void shortensLongLabelsAndGivesAnEmptyDiagramNoSize() {
        assertThat(DiagramLayout.shorten("a".repeat(60)))
                .hasSize(DiagramLayout.LABEL_LIMIT)
                .endsWith("...");
        assertThat(DiagramLayout.shorten("two\nlines")).isEqualTo("two lines");
        assertThat(DiagramLayout.of(new Diagram(List.of(), List.of())).boxes()).isEmpty();
    }

    @Test
    void pointsTheArrowHeadAlongTheArrow() {
        Arrow right = new Arrow(0, 0, 5, 0, 5, 0, 10, 0, "", false);
        Arrow degenerate = new Arrow(0, 0, 10, 0, 10, 0, 10, 0, "", false);

        assertThat(ArrowHead.of(right)).containsExactly(10, 0, 2, 3.2, 2, -3.2);
        assertThat(ArrowHead.of(degenerate)).containsExactly(10, 0, 2, 3.2, 2, -3.2);
        assertThat(right.labelX()).isEqualTo(5);
        assertThat(right.labelY()).isZero();
    }

    private static Box box(Placed placed, String label) {
        return placed.boxes().stream()
                .filter(box -> box.label().equals(label))
                .findFirst()
                .orElseThrow();
    }

    private static Diagram.Node node(String id) {
        return new Diagram.Node(id, id, "");
    }

    private static Diagram.Edge edge(String from, String to) {
        return new Diagram.Edge(from, to, "", false);
    }
}
