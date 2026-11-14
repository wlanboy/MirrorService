package com.wlanboy.mirrorservice.controller;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

@RestController
@Tag(name = "Echo", description = "Gibt den empfangenen Request als JSON zurück. Zeigt, welche Header, Client-Adresse und welcher Pfad nach Ingress, Istio oder Proxy beim Dienst ankommen.")
public class EchoController {

    static final int MAX_BODY_BYTES = 64 * 1024;

    private static final String MASK = "***";

    @Operation(summary = "Request-Echo", description = """
        Gibt Methode, URL, Pfad, Query, Header, Client- und Peer-Adresse sowie den Body als JSON zurück. \
        Die Header stammen aus dem unveränderten Request, Forwarded und X-Forwarded-* sind also enthalten. \
        Werte von Authorization, Proxy-Authorization und Cookie werden maskiert. \
        Bodies über 64 KiB werden abgeschnitten.""")
    @RequestMapping(path = "/echo/**", method = {RequestMethod.GET, RequestMethod.HEAD, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS})
    public ResponseEntity<EchoResponse> echo(HttpServletRequest request) throws IOException {
        // Mit forward-headers-strategy=framework ist der Request in ForwardedHeaderFilter verpackt.
        // Der liefert die effektive Client-Adresse und URL, blendet aber die Forwarded-Header aus.
        // Für Header und Peer-Adresse wird deshalb der ursprüngliche Request gelesen.
        var raw = unwrap(request);
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        boolean truncated = body.length > MAX_BODY_BYTES;
        int length = Math.min(body.length, MAX_BODY_BYTES);

        var echo = new EchoResponse(
            request.getMethod(),
            request.getRequestURL().toString(),
            request.getRequestURI(),
            request.getQueryString(),
            request.getRemoteAddr(),
            raw.getRemoteAddr(),
            headers(raw),
            new String(body, 0, length, charset(request)),
            length,
            truncated);

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(MirrorController.CONTENT_SECURITY_POLICY, "sandbox")
            .header(MirrorController.CONTENT_TYPE_OPTIONS, "nosniff")
            .body(echo);
    }

    private static HttpServletRequest unwrap(HttpServletRequest request) {
        while (request instanceof HttpServletRequestWrapper wrapper
                && wrapper.getRequest() instanceof HttpServletRequest inner) {
            request = inner;
        }
        return request;
    }

    private static Map<String, List<String>> headers(HttpServletRequest request) {
        Map<String, List<String>> headers = new LinkedCaseInsensitiveMap<>(Locale.ROOT);
        for (String name : Collections.list(request.getHeaderNames())) {
            if (headers.containsKey(name)) {
                continue;
            }
            List<String> values = new ArrayList<>();
            for (String value : Collections.list(request.getHeaders(name))) {
                values.add(mask(name, value));
            }
            headers.put(name, values);
        }
        return headers;
    }

    static String mask(String name, String value) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            // Das Schema (Bearer, Basic, ...) bleibt sichtbar, nur die Credentials werden ersetzt.
            case "authorization", "proxy-authorization" -> {
                int space = value.indexOf(' ');
                yield space > 0 ? value.substring(0, space) + " " + MASK : MASK;
            }
            // Cookie-Namen bleiben sichtbar, damit man sieht, welche Cookies ankommen.
            case "cookie" -> Arrays.stream(value.split(";"))
                .map(String::strip)
                .filter(pair -> !pair.isEmpty())
                .map(pair -> {
                    int eq = pair.indexOf('=');
                    return eq >= 0 ? pair.substring(0, eq) + "=" + MASK : MASK;
                })
                .collect(Collectors.joining("; "));
            default -> value;
        };
    }

    private static Charset charset(HttpServletRequest request) {
        String encoding = request.getCharacterEncoding();
        if (encoding != null) {
            try {
                return Charset.forName(encoding);
            } catch (IllegalArgumentException e) {
                // Unbekannter oder ungültiger Zeichensatz im Content-Type: UTF-8 verwenden.
            }
        }
        return StandardCharsets.UTF_8;
    }
}
