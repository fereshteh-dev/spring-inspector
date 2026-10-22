package io.github.fereshtehdev.springinspector.intellij.ui;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import io.github.fereshtehdev.springinspector.intellij.report.Report;
import io.github.fereshtehdev.springinspector.intellij.run.InspectorRunner;
import io.github.fereshtehdev.springinspector.intellij.settings.InspectorSettings;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps the vulnerability advisories current without the user running {@code update}: after a run whose report
 * shows them missing or older than a day, they are refreshed in the background for the next run. It can be turned
 * off in the settings; only public advisory data is downloaded.
 */
final class AdvisoryRefresh {

    static final Duration MAX_AGE = Duration.ofDays(1);
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private AdvisoryRefresh() {}

    static void afterRun(Project project, Report report) {
        if (!InspectorSettings.getInstance().updateAdvisories()
                || !isDue(report, Instant.now())
                || !RUNNING.compareAndSet(false, true)) {
            return;
        }
        boolean missing = report.advisoriesUpdatedAt().isEmpty();
        new Task.Backgroundable(project, "Spring Inspector: updating vulnerability advisories", true) {
            private Optional<String> failure = Optional.empty();

            @Override
            public void run(ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                failure = InspectorRunner.updateAdvisories(indicator);
            }

            @Override
            public void onFinished() {
                RUNNING.set(false);
                if (failure.isEmpty() && missing) {
                    tell(
                            project,
                            "Vulnerability advisories downloaded. Run Spring Inspector again to check your"
                                    + " dependencies.",
                            NotificationType.INFORMATION);
                } else if (failure.isPresent() && missing) {
                    tell(
                            project,
                            "Vulnerability advisories could not be downloaded: " + failure.get(),
                            NotificationType.WARNING);
                }
            }
        }.queue();
    }

    /**
     * Whether the report's advisories need a refresh. A report without any advisory information comes from a run in
     * which dependencies are not checked, so nothing is due then.
     */
    static boolean isDue(Report report, Instant now) {
        Optional<String> updatedAt = report.advisoriesUpdatedAt();
        if (updatedAt.isEmpty()) {
            return report.advisoriesWarning().isPresent();
        }
        try {
            return Instant.parse(updatedAt.get()).isBefore(now.minus(MAX_AGE));
        } catch (DateTimeParseException unreadable) {
            return true;
        }
    }

    private static void tell(Project project, String message, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Spring Inspector")
                .createNotification(message, type)
                .notify(project);
    }
}
