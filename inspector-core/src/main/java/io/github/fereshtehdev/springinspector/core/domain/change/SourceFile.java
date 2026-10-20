package io.github.fereshtehdev.springinspector.core.domain.change;

import io.github.fereshtehdev.springinspector.core.domain.LineRange;
import io.github.fereshtehdev.springinspector.core.domain.ProjectPath;
import java.nio.file.Path;
import java.util.List;

public record SourceFile(Path path, String content) {

    public SourceFile {
        path = ProjectPath.requireRelative(path);
    }

    public String snippet(LineRange lines) {
        List<String> all = content.lines().toList();
        if (lines.start() > all.size()) {
            return "";
        }
        return String.join("\n", all.subList(lines.start() - 1, Math.min(lines.end(), all.size())));
    }
}
