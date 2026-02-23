package com.wlanboy.mirrorservice.controller;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ergebnis einer Ping-Anfrage mit Erreichbarkeit, aufgelöster IP und Antwortzeit.")
public record PingResult(
    @Schema(description = "Angefragter Hostname", example = "google.com")
    String hostname,

    @Schema(description = "Aufgelöste IP-Adresse", example = "142.250.185.46")
    String resolvedIp,

    @Schema(description = "Gibt an, ob der Host erreichbar war", example = "true")
    boolean reachable,

    @Schema(description = "Antwortzeit in Millisekunden", example = "12")
    long responseTimeMs
) {}
