package com.wlanboy.mirrorservice.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Map;

@Schema(description = "Instruktion für den Mirror-Service: definiert Statuscode, Verzögerung, Body und Headers der gespiegelten Antwort.")
public record MirrorInstruction(
    @Schema(description = "HTTP Statuscode der Antwort", defaultValue = "200", minimum = "100", maximum = "599")
    @Min(value = 100, message = "StatusCode muss mindestens 100 sein")
    @Max(value = 599, message = "StatusCode darf maximal 599 sein")
    int statusCode,

    @Schema(description = "Wartezeit in Millisekunden", defaultValue = "0", minimum = "0", maximum = "60000")
    @Min(value = 0, message = "WaitMs darf nicht negativ sein")
    @Max(value = 60000, message = "WaitMs darf maximal 60000 sein")
    int waitMs,

    @Schema(description = "Inhalt der Antwort", example = "Hello World")
    String responseBody,

    @Schema(
        description = "Zusätzliche Response-Header als Key-Value-Paare",
        additionalProperties = Schema.AdditionalPropertiesValue.USE_ADDITIONAL_PROPERTIES_ANNOTATION,
        example = "{\"X-Custom-Header\": \"Value\"}"
    )
    Map<String, String> responseHeaders
) {
    public MirrorInstruction {
        if (statusCode == 0) statusCode = 200;
        if (waitMs < 0) waitMs = 0;
        if (responseHeaders == null) responseHeaders = Map.of();
    }
}
