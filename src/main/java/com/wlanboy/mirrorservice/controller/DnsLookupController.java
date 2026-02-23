package com.wlanboy.mirrorservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@Tag(name = "DNS Lookup", description = "Führt DNS-Auflösungen und Erreichbarkeitstests für Hostnamen durch.")
public class DnsLookupController {

    private final DnsResolver dnsResolver;

    public DnsLookupController(DnsResolver dnsResolver) {
        this.dnsResolver = dnsResolver;
    }

    @Operation(
        summary = "Host anpingen",
        description = "Prüft die Erreichbarkeit eines Hosts und gibt Antwortzeit sowie aufgelöste IP zurück."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Host ist erreichbar",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "Erreichbar", value = "{\"hostname\": \"google.com\", \"resolvedIp\": \"142.250.185.46\", \"reachable\": true, \"responseTimeMs\": 12}")
            })
        ),
        @ApiResponse(responseCode = "408", description = "Host nicht erreichbar (Timeout)",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "Timeout", value = "{\"hostname\": \"192.0.2.1\", \"resolvedIp\": \"192.0.2.1\", \"reachable\": false, \"responseTimeMs\": 1000}")
            })
        ),
        @ApiResponse(responseCode = "404", description = "Hostname konnte nicht aufgelöst werden",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "Unbekannter Host", value = "{\"hostname\": \"unbekannt.local\", \"resolvedIp\": null, \"reachable\": false, \"responseTimeMs\": 0}")
            })
        ),
        @ApiResponse(responseCode = "500", description = "Unerwarteter Fehler",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "Fehler", value = "{\"hostname\": \"example.com\", \"resolvedIp\": null, \"reachable\": false, \"responseTimeMs\": 0}")
            })
        )
    })
    @GetMapping("/ping/{hostname}")
    public Mono<ResponseEntity<PingResult>> ping(
            @PathVariable String hostname,
            @Schema(description = "Timeout in Millisekunden", example = "1000")
            @RequestParam(defaultValue = "1000") int timeoutMs) {
        return Mono.fromCallable(() -> {
            InetAddress address = dnsResolver.getByName(hostname);
            long start = System.currentTimeMillis();
            boolean reachable = dnsResolver.isReachable(address, timeoutMs);
            long elapsed = System.currentTimeMillis() - start;
            PingResult result = new PingResult(hostname, address.getHostAddress(), reachable, elapsed);
            return reachable
                ? ResponseEntity.ok(result)
                : ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(result);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorResume(UnknownHostException.class, e ->
            Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new PingResult(hostname, null, false, 0)))
        )
        .onErrorResume(Exception.class, e ->
            Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new PingResult(hostname, null, false, 0)))
        );
    }

    @Operation(
        summary = "DNS-Auflösung durchführen",
        description = "Führt eine lokale DNS-Auflösung für den angegebenen Hostnamen durch und gibt die gefundenen IP-Adressen zurück."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "DNS-Auflösung erfolgreich",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "IPv4", summary = "Einzelne IPv4-Adresse", value = "[\"93.184.216.34\"]"),
                @ExampleObject(name = "IPv4 + IPv6", summary = "Mehrere Adressen", value = "[\"93.184.216.34\", \"2606:2800:21f:cb07:6820:80da:af6b:8b2c\"]")
            })
        ),
        @ApiResponse(responseCode = "404", description = "Hostname konnte nicht aufgelöst werden",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "Unbekannter Host", value = "[\"Hostname 'unbekannt.local' konnte nicht aufgelöst werden.\"]")
            })
        ),
        @ApiResponse(responseCode = "403", description = "DNS-Auflösung wegen Sicherheitseinschränkungen verweigert",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "SecurityManager", value = "[\"DNS-Auflösung aufgrund von Sicherheitseinschränkungen verweigert.\"]")
            })
        ),
        @ApiResponse(responseCode = "500", description = "Unerwarteter Fehler",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "Unbekannter Fehler", value = "[\"Ein unerwarteter Fehler ist aufgetreten.\"]")
            })
        )
    })
    @GetMapping("/resolve/{hostname}")
    public Mono<ResponseEntity<List<String>>> resolveDns(@PathVariable String hostname) {
        return Mono.fromCallable(() -> {
            InetAddress[] addresses = dnsResolver.getAllByName(hostname);
            if (addresses.length > 0) {
                List<String> ipAddresses = Arrays.stream(addresses)
                    .map(InetAddress::getHostAddress)
                    .toList();
                return ResponseEntity.ok(ipAddresses);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(List.of("Keine IP-Adressen für Hostname '" + hostname + "' gefunden."));
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorResume(UnknownHostException.class, e ->
            Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(List.of("Hostname '" + hostname + "' konnte nicht aufgelöst werden.")))
        )
        .onErrorResume(SecurityException.class, e ->
            Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(List.of("DNS-Auflösung aufgrund von Sicherheitseinschränkungen verweigert.")))
        )
        .onErrorResume(Exception.class, e ->
            Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(List.of("Ein unerwarteter Fehler ist aufgetreten.")))
        );
    }
}
