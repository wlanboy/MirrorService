package com.wlanboy.mirrorservice.controller;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request, wie er beim Dienst angekommen ist.")
public record EchoResponse(
    @Schema(description = "HTTP-Methode", example = "POST")
    String method,

    @Schema(description = "Effektive URL nach Auswertung von Forwarded/X-Forwarded-*", example = "https://mirror.example.com/echo/test?a=1")
    String url,

    @Schema(description = "Pfad, wie ihn die Anwendung sieht (inklusive X-Forwarded-Prefix)", example = "/echo/test")
    String path,

    @Schema(description = "Roher Query-String", example = "a=1", nullable = true)
    String query,

    @Schema(description = "Client-Adresse nach Auswertung von Forwarded/X-Forwarded-For", example = "203.0.113.7")
    String remoteAddress,

    @Schema(description = "Adresse der direkten TCP-Gegenstelle (Ingress, Sidecar oder Proxy)", example = "10.42.0.12")
    String peerAddress,

    @Schema(description = "Header, wie sie empfangen wurden, inklusive Forwarded/X-Forwarded-*. Werte von Authorization, Proxy-Authorization und Cookie sind maskiert.")
    Map<String, List<String>> headers,

    @Schema(description = "Body als Text, höchstens 64 KiB", example = "{\"hello\": \"world\"}")
    String body,

    @Schema(description = "Anzahl der zurückgegebenen Body-Bytes", example = "18")
    int bodyBytes,

    @Schema(description = "true, wenn der Body länger als 64 KiB war und abgeschnitten wurde", example = "false")
    boolean bodyTruncated
) {}
