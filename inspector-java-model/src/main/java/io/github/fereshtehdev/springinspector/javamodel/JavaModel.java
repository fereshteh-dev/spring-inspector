package io.github.fereshtehdev.springinspector.javamodel;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.ProjectPath;
import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.core.domain.project.ProjectContext;
import io.github.fereshtehdev.springinspector.core.domain.project.RuleOptions;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import io.github.fereshtehdev.springinspector.core.port.SourceProvider;
import io.github.fereshtehdev.springinspector.javamodel.cache.CacheKey;
import io.github.fereshtehdev.springinspector.javamodel.cache.FindingCache;
import io.github.fereshtehdev.springinspector.javamodel.patch.LineIndex;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * One inspection run's view of the project's Java sources. Pass it as the run's {@link SourceProvider} so every
 * {@link JavaCheck} shares it: each file is read, hashed and parsed at most once, and with a {@link FindingCache} a
 * check skips files whose result it already knows.
 */
public final class JavaModel implements SourceProvider {

    static final int MAX_SOURCE_CHARS = 2 * 1024 * 1024;

    private final SourceProvider sources;
    private final FindingCache cache;
    private final CheckFingerprints fingerprints;
    private final Lazy<ParserConfiguration> configuration;
    private final String context;
    private final Resolver resolver = new Resolver();
    private final ConcurrentMap<Path, Lazy<Optional<SourceFile>>> reads = new ConcurrentHashMap<>();
    private final ConcurrentMap<Path, Lazy<Optional<String>>> hashes = new ConcurrentHashMap<>();
    private final ConcurrentMap<Path, Lazy<Optional<ParsedUnit>>> units = new ConcurrentHashMap<>();
    private final ConcurrentMap<Path, String> unparseable = new ConcurrentHashMap<>();

    public JavaModel(ProjectContext project, SourceProvider sources) {
        this(project, sources, FindingCache.disabled());
    }

    public JavaModel(ProjectContext project, SourceProvider sources, FindingCache cache) {
        this(project, sources, cache, CheckFingerprints.fromCodeSource());
    }

    JavaModel(ProjectContext project, SourceProvider sources, FindingCache cache, CheckFingerprints fingerprints) {
        this.sources = sources;
        this.cache = cache;
        this.fingerprints = fingerprints;
        List<Path> sourceRoots = SourceRoots.of(project);
        // Built on first parse: a run answered entirely from the cache never loads the parser or symbol solver, whose
        // language-level enum alone instantiates a validator for every Java release.
        this.configuration = new Lazy<>(() -> new ParserConfiguration()
                .setLanguageLevel(LanguageLevels.forJava(project.versions().java()))
                .setSymbolResolver(new JavaSymbolSolver(new ProjectTypeSolver(sourceRoots, this::parseAsDependency))));
        this.context = "java" + LanguageLevels.feature(project.versions().java()) + ";" + project.versions() + ";jdk"
                + Runtime.version().feature() + ";" + sourceRoots;
    }

    /** The run's shared model, or a private one when the caller did not provide it. */
    public static JavaModel of(InspectionInput input) {
        return input.sources() instanceof JavaModel model ? model : new JavaModel(input.project(), input.sources());
    }

    @Override
    public Optional<SourceFile> read(Path projectRelativePath) {
        Path path = ProjectPath.requireRelative(projectRelativePath);
        return reads.computeIfAbsent(path, key -> new Lazy<>(() -> sources.read(key)))
                .get();
    }

    /** Files that could not be parsed in this run, with the reason, in path order. */
    public Map<Path, String> unparseableFiles() {
        return new TreeMap<>(unparseable);
    }

    List<Finding> findings(JavaCheck check, Path file, RuleOptions options) {
        Optional<String> contentHash = contentHash(file);
        if (contentHash.isEmpty()) {
            return List.of();
        }
        Optional<CacheKey> key = cache.enabled()
                ? fingerprints
                        .of(check)
                        .map(code -> new CacheKey(
                                code + ";" + options.fingerprint() + ";" + context, file, contentHash.get()))
                : Optional.empty();
        if (key.isPresent()) {
            Optional<List<Finding>> cached = cache.lookup(key.get(), this::contentHash);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        DependencyRecorder recorder = new DependencyRecorder();
        List<Finding> findings = parse(file)
                .map(unit -> check.inspect(new JavaFile(unit, resolver, recorder, options)))
                .orElse(List.of());
        key.ifPresent(cacheKey -> cache.store(cacheKey, findings, recorder.dependencies()));
        return findings;
    }

    Optional<String> contentHash(Path file) {
        return hashes.computeIfAbsent(
                        file, key -> new Lazy<>(() -> read(key).map(source -> ContentHash.of(source.content()))))
                .get();
    }

    Optional<ParsedUnit> parse(Path file) {
        Path path = ProjectPath.requireRelative(file);
        return units.computeIfAbsent(path, key -> new Lazy<>(() -> read(key).flatMap(this::parseNow)))
                .get();
    }

    private Optional<ParsedUnit> parseAsDependency(Path file) {
        resolver.dependsOn(file, contentHash(file));
        return parse(file);
    }

    private Optional<ParsedUnit> parseNow(SourceFile source) {
        if (source.content().length() > MAX_SOURCE_CHARS) {
            unparseable.put(source.path(), "larger than " + MAX_SOURCE_CHARS + " characters");
            return Optional.empty();
        }
        try {
            ParseResult<CompilationUnit> result = new JavaParser(configuration.get()).parse(source.content());
            Optional<CompilationUnit> unit = result.getResult();
            if (result.isSuccessful() && unit.isPresent()) {
                return Optional.of(new ParsedUnit(source, unit.get(), LineIndex.of(source.content())));
            }
            unparseable.put(
                    source.path(),
                    result.getProblems().stream()
                            .findFirst()
                            .map(Problem::getVerboseMessage)
                            .orElse("no syntax tree"));
            return Optional.empty();
        } catch (RuntimeException | StackOverflowError e) {
            unparseable.put(source.path(), "parser failed: " + e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
