package io.github.fereshtehdev.springinspector.core.port;

import io.github.fereshtehdev.springinspector.core.domain.report.ReportCard;
import java.io.IOException;

public interface ReportRenderer {

    void render(ReportCard card, Appendable out) throws IOException;
}
