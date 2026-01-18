package com.wlanboy.mirrorservice.controller;

import java.util.concurrent.Executor;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/mirror")
@Tag(name = "Mirror", description = "Mirror-Service-Endpunkte")
public class MirrorController {

    private final Executor taskExecutor;

    public MirrorController(@Qualifier("mirrorTaskExecutor") Executor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    @Operation(summary = "Mirror GET Request", description = "Spiegelt den GET-Request basierend auf den Query-Parametern.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Erfolgreiche Antwort mit konfiguriertem Status"),
        @ApiResponse(responseCode = "500", description = "Interner Serverfehler")
    })
    @RequestMapping(method = {RequestMethod.GET})
    public DeferredResult<ResponseEntity<String>> mirrorGet(
            @ParameterObject MirrorInstruction instruction) {
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
    public DeferredResult<ResponseEntity<String>> mirror(@Valid @RequestBody MirrorInstruction instruction) {
        return executeMirror(instruction);
    }

    private DeferredResult<ResponseEntity<String>> executeMirror(MirrorInstruction instruction) {
        DeferredResult<ResponseEntity<String>> deferredResult = new DeferredResult<>(instruction.waitMs() + 5000L);

        taskExecutor.execute(() -> {
            try {
                if (instruction.waitMs() > 0) {
                    Thread.sleep(Math.min(instruction.waitMs(), 60000));
                }

                var response = ResponseEntity.status(instruction.statusCode());
                instruction.responseHeaders().forEach(response::header);

                deferredResult.setResult(response.body(instruction.responseBody()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                deferredResult.setErrorResult(ResponseEntity.internalServerError().build());
            } catch (Exception e) {
                deferredResult.setErrorResult(ResponseEntity.internalServerError().build());
            }
        });

        return deferredResult;
    }
}