package com.acme.reports;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
class ReportService {

    private final Mailer mailer;
    private final ReportCache cache;

    ReportService(Mailer mailer, ReportCache cache) {
        this.mailer = mailer;
        this.cache = cache;
    }

    public void publish(Report report) {
        render(report);
        mailer.sendByMail(report);
    }

    public Report latest(String customer) {
        return cache.find(customer);
    }

    @Async
    public void archive(Report report) {
        archive(report.previous());
    }

    @Cacheable("templates")
    public String template(String name) {
        return name;
    }

    private void render(Report report) {}
}
