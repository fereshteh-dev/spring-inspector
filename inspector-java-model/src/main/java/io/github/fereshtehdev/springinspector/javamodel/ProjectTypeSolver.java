package io.github.fereshtehdev.springinspector.javamodel;

import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.resolution.Navigator;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Resolves JDK types by reflection (JDK classes only, never the inspected code) and project types by parsing their
 * sources through the run's {@link JavaModel}, so a staged snapshot resolves against staged content and every source
 * read goes through the same limits.
 */
final class ProjectTypeSolver implements TypeSolver {

    private final TypeSolver jdk = new ReflectionTypeSolver(true);
    private final List<Path> sourceRoots;
    private final Function<Path, Optional<ParsedUnit>> sources;
    private @Nullable TypeSolver parent;

    ProjectTypeSolver(List<Path> sourceRoots, Function<Path, Optional<ParsedUnit>> sources) {
        this.sourceRoots = List.copyOf(sourceRoots);
        this.sources = sources;
        jdk.setParent(this);
    }

    @Override
    public @Nullable TypeSolver getParent() {
        return parent;
    }

    @Override
    public void setParent(TypeSolver parent) {
        this.parent = parent;
    }

    @Override
    public SymbolReference<ResolvedReferenceTypeDeclaration> tryToSolveType(String name) {
        SymbolReference<ResolvedReferenceTypeDeclaration> fromJdk = jdk.tryToSolveType(name);
        return fromJdk.isSolved() ? fromJdk : fromSources(name);
    }

    @Override
    public SymbolReference<ResolvedReferenceTypeDeclaration> tryToSolveTypeInModule(String module, String name) {
        return tryToSolveType(name);
    }

    /** {@code a.b.C.D} may be type {@code D} nested in {@code a/b/C.java}, or top-level type {@code a/b/C/D.java}. */
    private SymbolReference<ResolvedReferenceTypeDeclaration> fromSources(String name) {
        List<String> parts = List.of(name.split("[.]", -1));
        for (int fileParts = parts.size(); fileParts > 0; fileParts--) {
            String file = String.join("/", parts.subList(0, fileParts)) + ".java";
            String typeName = String.join(".", parts.subList(fileParts - 1, parts.size()));
            for (Path root : sourceRoots) {
                Optional<TypeDeclaration<?>> type =
                        parse(root, file).flatMap(unit -> Navigator.findType(unit.unit(), typeName));
                if (type.isPresent()) {
                    return SymbolReference.solved(
                            JavaParserFacade.get(getRoot()).getTypeDeclaration(type.get()));
                }
            }
        }
        return SymbolReference.unsolved();
    }

    private Optional<ParsedUnit> parse(Path root, String file) {
        try {
            return sources.apply(root.resolve(file));
        } catch (InvalidPathException notAPath) {
            return Optional.empty();
        }
    }
}
