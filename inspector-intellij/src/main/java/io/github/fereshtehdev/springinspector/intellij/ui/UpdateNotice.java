package io.github.fereshtehdev.springinspector.intellij.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.io.HttpRequests;
import io.github.fereshtehdev.springinspector.intellij.run.ReleaseVersions;
import io.github.fereshtehdev.springinspector.intellij.settings.InspectorSettings;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tells the user when a newer Spring Inspector is on GitHub, at most once a day, after a run. New rules arrive with new
 * versions; with the JetBrains Marketplace the IDE updates by itself, until then this notice does the telling. Only the
 * public release list is requested, through the IDE's own connection settings, and it can be turned off.
 */
final class UpdateNotice {

    static final String LATEST_RELEASE = "https://api.github.com/repos/fereshteh-dev/spring-inspector/releases/latest";
    static final String RELEASES_PAGE = "https://github.com/fereshteh-dev/spring-inspector/releases/latest";
    static final Duration INTERVAL = Duration.ofDays(1);
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private UpdateNotice() {}

    static void afterRun(Project project) {
        InspectorSettings settings = InspectorSettings.getInstance();
        Optional<String> current = ReleaseVersions.current();
        Instant now = Instant.now();
        if (!settings.notifyAboutUpdates()
                || current.isEmpty()
                || !settings.lastUpdateCheck().isBefore(now.minus(INTERVAL))
                || !RUNNING.compareAndSet(false, true)) {
            return;
        }
        settings.setLastUpdateCheck(now);
        new Task.Backgroundable(project, "Spring Inspector: checking for a new version", true) {
            private Optional<String> newer = Optional.empty();

            @Override
            public void run(ProgressIndicator indicator) {
                try {
                    String release = HttpRequests.request(LATEST_RELEASE)
                            .productNameAsUserAgent()
                            .connectTimeout(10_000)
                            .readTimeout(10_000)
                            .readString(indicator);
                    newer = ReleaseVersions.latest(release)
                            .filter(latest -> ReleaseVersions.isNewer(latest, current.get()));
                } catch (IOException unreachable) {
                    newer = Optional.empty();
                }
            }

            @Override
            public void onFinished() {
                RUNNING.set(false);
                newer.ifPresent(version -> tell(project, version, current.get()));
            }
        }.queue();
    }

    private static void tell(Project project, String version, String current) {
        Notification notification = NotificationGroupManager.getInstance()
                .getNotificationGroup("Spring Inspector")
                .createNotification(
                        "Spring Inspector " + version + " is available",
                        "You have " + current + ". New versions bring new and improved rules.",
                        NotificationType.INFORMATION);
        notification.addAction(NotificationAction.createSimpleExpiring("Download", () -> BrowserUtil.browse(RELEASES_PAGE)));
        notification.notify(project);
    }
}
