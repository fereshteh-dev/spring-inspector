package io.github.fereshtehdev.springinspector.document.design;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.EntityModel;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.Relation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The stored types of a project: JPA entities, MongoDB documents and Spring Data JDBC or R2DBC tables. */
final class Entities {

    private static final String[] STORED = {"Entity", "Document", "Table", "Node"};
    private static final String[] RELATIONS = {
        "OneToMany",
        "ManyToOne",
        "OneToOne",
        "ManyToMany",
        "Embedded",
        "ElementCollection",
        "DBRef",
        "DocumentReference",
        "MappedCollection"
    };

    private Entities() {}

    static Optional<EntityModel> of(TypeDeclaration<?> type) {
        if (!Annotations.has(type, STORED)) {
            return Optional.empty();
        }
        String store = Annotations.find(type, "Table", "Document")
                .flatMap(annotation -> Annotations.value(annotation, "name", "collection", "value"))
                .orElse("");
        List<Relation> relations = new ArrayList<>();
        String id = "";
        int fields = 0;
        for (Member member : members(type)) {
            fields++;
            if (id.isEmpty() && Annotations.has(member.annotated(), "Id", "EmbeddedId")) {
                id = member.name();
            }
            Optional<AnnotationExpr> relation = Annotations.find(member.annotated(), RELATIONS);
            if (relation.isPresent()) {
                relations.add(new Relation(
                        relation.get().getName().getIdentifier(), member.name(), elementType(member.type())));
            }
        }
        return Optional.of(new EntityModel(type.getNameAsString(), store, id, fields, relations));
    }

    private record Member(String name, Type type, NodeWithAnnotations<?> annotated) {}

    private static List<Member> members(TypeDeclaration<?> type) {
        List<Member> members = new ArrayList<>();
        if (type instanceof RecordDeclaration data) {
            for (Parameter component : data.getParameters()) {
                members.add(new Member(component.getNameAsString(), component.getType(), component));
            }
        }
        for (FieldDeclaration field : type.getFields()) {
            if (!field.isStatic()) {
                field.getVariables()
                        .forEach(variable ->
                                members.add(new Member(variable.getNameAsString(), variable.getType(), field)));
            }
        }
        return members;
    }

    /** The type a relation points at: {@code OrderLine} for {@code List<OrderLine>}. */
    private static String elementType(Type type) {
        if (type instanceof ClassOrInterfaceType declared
                && declared.getTypeArguments().isPresent()
                && !declared.getTypeArguments().get().isEmpty()) {
            return elementType(declared.getTypeArguments().get().getLast().orElseThrow());
        }
        return type.asString();
    }
}
