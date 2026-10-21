package com.acme.imports;

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
            } catch (Exception e) {
                log.warn("Skipping row {}", row, e);
            }
        }
        return imported;
    }

    void parse(String row) {}
}
