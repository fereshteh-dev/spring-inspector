package com.acme.quotes;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
class QuoteHandler {

    private final JdbcTemplate jdbc;
    private final PriceClient prices;

    QuoteHandler(JdbcTemplate jdbc, PriceClient prices) {
        this.jdbc = jdbc;
        this.prices = prices;
    }

    Mono<Quote> quote(String symbol) {
        return prices.latest(symbol).map(price -> new Quote(symbol, price, 0L));
    }

    Long volume(String symbol) {
        return jdbc.queryForObject("select volume from trades where symbol = ?", Long.class, symbol);
    }
}
