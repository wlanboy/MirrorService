package com.wlanboy.mirrorservice.controller;

import java.time.Duration;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/mirror")
@Tag(name = "Mirror", description = "Gibt HTTP-Antworten mit konfigurierbarem Statuscode, Headers, Body und optionaler Verzögerung zurück. Nützlich für Tests von Timeouts, Fehlerverhalten und Header-Verarbeitung.")
public class MirrorController {

    @Operation(summary = "Mirror GET/HEAD Request", description = "Spiegelt den GET/HEAD-Request basierend auf den Query-Parametern.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Erfolgreiche Antwort mit konfiguriertem Body",
            content = @Content(mediaType = "text/plain", examples = {
                @ExampleObject(name = "Einfache Antwort", value = "OK"),
                @ExampleObject(name = "JSON-Body", value = "{\"status\": \"healthy\"}")
            })
        ),
        @ApiResponse(responseCode = "4XX", description = "Konfigurierter Client-Fehler",
            content = @Content(mediaType = "text/plain", examples = {
                @ExampleObject(name = "Not Found (404)", summary = "statusCode=404", value = "Resource not found"),
                @ExampleObject(name = "Too Many Requests (429)", summary = "statusCode=429", value = "Rate limit exceeded")
            })
        ),
        @ApiResponse(responseCode = "5XX", description = "Konfigurierter Server-Fehler oder interner Fehler",
            content = @Content(mediaType = "text/plain", examples = {
                @ExampleObject(name = "Service Unavailable (503)", summary = "statusCode=503, waitMs=5000", value = "Service temporarily unavailable"),
                @ExampleObject(name = "Gateway Timeout (504)", summary = "statusCode=504, waitMs=30000", value = "Upstream timeout")
            })
        )
    })
    @RequestMapping(method = {RequestMethod.GET, RequestMethod.HEAD})
    public Mono<ResponseEntity<String>> mirrorGet(@ParameterObject MirrorInstruction instruction) {
        return executeMirror(instruction);
    }

    @Operation(
        summary = "Mirror Request",
        description = "Spiegelt den Request basierend auf den Instruktionen im JSON-Body.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = MirrorInstruction.class),
                examples = {
                    @ExampleObject(name = "Erfolg mit Body", summary = "200 OK mit JSON-Body",
                        value = "{\"statusCode\": 200, \"waitMs\": 0, \"responseBody\": \"{\\\"result\\\": \\\"ok\\\"}\", \"responseHeaders\": {\"Content-Type\": \"application/json\"}}"),
                    @ExampleObject(name = "Simulierter Fehler", summary = "503 mit Verzögerung",
                        value = "{\"statusCode\": 503, \"waitMs\": 3000, \"responseBody\": \"Service unavailable\", \"responseHeaders\": {\"Retry-After\": \"30\"}}"),
                    @ExampleObject(name = "Timeout-Test", summary = "200 mit 10s Verzögerung",
                        value = "{\"statusCode\": 200, \"waitMs\": 10000, \"responseBody\": \"Delayed response\", \"responseHeaders\": {}}")
                }
            )
        )
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Erfolgreiche Antwort mit konfiguriertem Body",
            content = @Content(mediaType = "text/plain", examples = {
                @ExampleObject(name = "Einfache Antwort", value = "OK"),
                @ExampleObject(name = "JSON-Body", value = "{\"result\": \"ok\"}")
            })
        ),
        @ApiResponse(responseCode = "4XX", description = "Konfigurierter Client-Fehler",
            content = @Content(mediaType = "text/plain", examples = {
                @ExampleObject(name = "Bad Request (400)", summary = "statusCode=400", value = "Invalid input"),
                @ExampleObject(name = "Unauthorized (401)", summary = "statusCode=401", value = "Authentication required")
            })
        ),
        @ApiResponse(responseCode = "5XX", description = "Konfigurierter Server-Fehler oder interner Fehler",
            content = @Content(mediaType = "text/plain", examples = {
                @ExampleObject(name = "Internal Server Error (500)", summary = "statusCode=500", value = "Unexpected error"),
                @ExampleObject(name = "Service Unavailable (503)", summary = "statusCode=503, waitMs=3000", value = "Service temporarily unavailable")
            })
        )
    })
    @RequestMapping(method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public Mono<ResponseEntity<String>> mirror(@Valid @RequestBody MirrorInstruction instruction) {
        return executeMirror(instruction);
    }

    private Mono<ResponseEntity<String>> executeMirror(MirrorInstruction instruction) {
        Mono<ResponseEntity<String>> result = Mono.fromSupplier(() -> {
            var response = ResponseEntity.status(instruction.statusCode());
            instruction.responseHeaders().forEach(response::header);
            return response.body(instruction.responseBody());
        });

        if (instruction.waitMs() > 0) {
            return Mono.delay(Duration.ofMillis(instruction.waitMs())).then(result);
        }
        return result;
    }
}
