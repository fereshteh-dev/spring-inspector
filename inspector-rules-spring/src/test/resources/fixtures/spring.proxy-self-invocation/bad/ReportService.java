package com.acme.reports;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
class ReportService {

    public void publish(Report report) {
        render(report);
        this.sendByMail(report);
    }

    public Report latest(String customer) {
        return find(customer);
    }

    @Async
    public void sendByMail(Report report) {}

    @Cacheable("reports")
    public Report find(String customer) {
        return new Report(customer);
    }

    private void render(Report report) {}
}
