package com.wlanboy.mirrorservice.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record MirrorInstruction(
    @Schema(description = "HTTP Statuscode der Antwort", defaultValue = "200")
    int statusCode,
    @Schema(description = "Wartezeit in Millisekunden", defaultValue = "0")
    int waitMs,
    @Schema(description = "Inhalt der Antwort")
    String responseBody,
    @Schema(
        description = "Zusätzliche Header",
        // Das hier ist entscheidend für die JSON-Darstellung im POST
        additionalProperties = Schema.AdditionalPropertiesValue.USE_ADDITIONAL_PROPERTIES_ANNOTATION,
        example = "{\"X-Custom-Header\": \"Value\"}"
    )
    Map<String, String> responseHeaders
) {
    public MirrorInstruction {
        if (statusCode == 0) statusCode = 200;
        if (responseHeaders == null) responseHeaders = Map.of();
    }
}