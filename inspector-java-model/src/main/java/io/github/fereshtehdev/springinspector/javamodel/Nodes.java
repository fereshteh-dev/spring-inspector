package io.github.fereshtehdev.springinspector.javamodel;

import com.github.javaparser.ast.Node;
import java.util.Optional;

/** Syntax tree navigation that JavaParser only offers through generic varargs. */
public final class Nodes {

    private Nodes() {}

    /** The closest enclosing node of the given type, not counting the node itself. */
    public static <T> Optional<T> ancestor(Node node, Class<T> type) {
        Optional<Node> current = node.getParentNode();
        while (current.isPresent() && !type.isInstance(current.get())) {
            current = current.get().getParentNode();
        }
        return current.map(type::cast);
    }
}
