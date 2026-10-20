package io.github.fereshtehdev.springinspector.core.domain.project;

public record Applicability(VersionRequirement java, VersionRequirement springBoot, VersionRequirement springAi) {

    public static Applicability always() {
        VersionRequirement any = VersionRequirement.any();
        return new Applicability(any, any, any);
    }

    public Applicability requiringJava(VersionRange range) {
        return new Applicability(VersionRequirement.within(range), springBoot, springAi);
    }

    public Applicability requiringSpringBoot(VersionRange range) {
        return new Applicability(java, VersionRequirement.within(range), springAi);
    }

    public Applicability requiringSpringAi(VersionRange range) {
        return new Applicability(java, springBoot, VersionRequirement.within(range));
    }

    public boolean appliesTo(DetectedVersions versions) {
        return java.isSatisfiedBy(versions.java())
                && springBoot.isSatisfiedBy(versions.springBoot())
                && springAi.isSatisfiedBy(versions.springAi());
    }
}
