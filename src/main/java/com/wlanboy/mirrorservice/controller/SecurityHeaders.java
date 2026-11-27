package com.wlanboy.mirrorservice.controller;

import org.springframework.http.HttpHeaders;

/**
 * Schutz-Header für Antworten mit vom Aufrufer bestimmtem Inhalt. {@code Content-Security-Policy:
 * sandbox} sperrt Skripte und gibt dem Dokument einen opaken Ursprung, {@code nosniff} verhindert,
 * dass der Browser einen text/plain-Body als HTML interpretiert.
 */
final class SecurityHeaders {

    static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    static final String CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";

    private SecurityHeaders() {
    }

    /** Setzt die Schutz-Header, sofern sie nicht schon gesetzt sind. */
    static HttpHeaders applyDefaults(HttpHeaders headers) {
        if (!headers.containsHeader(CONTENT_SECURITY_POLICY)) {
            headers.set(CONTENT_SECURITY_POLICY, "sandbox");
        }
        if (!headers.containsHeader(CONTENT_TYPE_OPTIONS)) {
            headers.set(CONTENT_TYPE_OPTIONS, "nosniff");
        }
        return headers;
    }
}
