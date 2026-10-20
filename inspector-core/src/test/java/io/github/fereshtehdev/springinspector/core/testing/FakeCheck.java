package io.github.fereshtehdev.springinspector.core.testing;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.project.Applicability;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class FakeCheck implements Check {

    @FunctionalInterface
    public interface Behaviour {
        List<Finding> inspect(InspectionInput input) throws Exception;
    }

    private final RuleId id;
    private final Category category;
    private final Applicability applicability;
    private final Behaviour behaviour;
    private final AtomicInteger invocations = new AtomicInteger();

    private FakeCheck(RuleId id, Category category, Applicability applicability, Behaviour behaviour) {
        this.id = id;
        this.category = category;
        this.applicability = applicability;
        this.behaviour = behaviour;
    }

    public static FakeCheck reporting(String id, Category category, Finding... findings) {
        return behaving(id, category, input -> List.of(findings));
    }

    public static FakeCheck behaving(String id, Category category, Behaviour behaviour) {
        return new FakeCheck(RuleId.of(id), category, Applicability.always(), behaviour);
    }

    public FakeCheck applicableWhen(Applicability applicability) {
        return new FakeCheck(id, category, applicability, behaviour);
    }

    public int invocations() {
        return invocations.get();
    }

    @Override
    public RuleId id() {
        return id;
    }

    @Override
    public Category category() {
        return category;
    }

    @Override
    public Applicability applicability() {
        return applicability;
    }

    @Override
    public List<Finding> inspect(InspectionInput input) {
        invocations.incrementAndGet();
        try {
            return behaviour.inspect(input);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
