package io.github.fereshtehdev.springinspector.archtests.fixtures.leaky.core;

import java.net.http.HttpClient;

public final class HttpCallingCore {

    public HttpClient.Version preferredVersion() {
        return HttpClient.Version.HTTP_2;
    }
}
