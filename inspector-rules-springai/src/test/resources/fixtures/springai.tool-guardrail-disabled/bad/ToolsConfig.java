package com.acme;

import org.springframework.ai.model.tool.ToolCallingManager;

class ToolsConfig {
    ToolCallingManager manager() {
        return ToolCallingManager.builder()
                .unlimitedCallsPerTool()
                .unlimitedTotalToolCalls()
                .resolutionFallbackEnabled(true)
                .build();
    }
}
