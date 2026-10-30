package com.acme;

import org.springframework.ai.tool.annotation.Tool;

class WeatherTools {
    @Tool(description = "The forecast for a city for the next three days")
    String forecast(String city) {
        return "";
    }

    String notATool(String city) {
        return "";
    }
}
