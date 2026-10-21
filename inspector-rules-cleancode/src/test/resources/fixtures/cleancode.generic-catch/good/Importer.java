package com.acme.imports;

import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Importer {

    private static final Logger log = LoggerFactory.getLogger(Importer.class);

    int importAll(Iterable<String> rows) {
        int imported = 0;
        for (String row : rows) {
            try {
                parse(row);
                imported++;
            } catch (DateTimeParseException | NumberFormatException e) {
                log.warn("Skipping malformed row {}", row, e);
            }
        }
        return imported;
    }

    void inTransaction(Runnable work) {
        try {
            work.run();
        } catch (RuntimeException e) {
            rollback();
            throw e;
        }
    }

    void parse(String row) {}

    void rollback() {}
}
