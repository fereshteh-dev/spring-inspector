package com.acme;

import org.springframework.ai.tool.annotation.Tool;

class WeatherTools {
    @Tool
    String forecast(String city) {
        return "";
    }

    @Tool(name = "temperature")
    int temperature(String city) {
        return 0;
    }

    @Tool(description = "")
    int humidity(String city) {
        return 0;
    }
}
