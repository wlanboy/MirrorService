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
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/mirror")
@Tag(name = "Mirror", description = "Mirror-Service-Endpunkte")
public class MirrorController {

    @Operation(summary = "Mirror GET Request", description = "Spiegelt den GET-Request basierend auf den Query-Parametern.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Erfolgreiche Antwort mit konfiguriertem Status"),
        @ApiResponse(responseCode = "500", description = "Interner Serverfehler")
    })
    @RequestMapping(method = {RequestMethod.GET})
    public Mono<ResponseEntity<String>> mirrorGet(@ParameterObject MirrorInstruction instruction) {
        return executeMirror(instruction);
    }

    @Operation(
        summary = "Mirror Request",
        description = "Spiegelt den Request basierend auf den Instruktionen im JSON-Body.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MirrorInstruction.class))
        )
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Erfolgreiche Antwort mit konfiguriertem Status"),
        @ApiResponse(responseCode = "500", description = "Interner Serverfehler")
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
