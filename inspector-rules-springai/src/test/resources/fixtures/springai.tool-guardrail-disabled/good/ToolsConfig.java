package com.acme;

import org.springframework.ai.model.tool.ToolCallingManager;

class ToolsConfig {
    ToolCallingManager manager() {
        return ToolCallingManager.builder()
                .maxCallsPerTool("search", 10)
                .maxTotalToolCalls(50)
                .resolutionFallbackEnabled(false)
                .build();
    }
}
