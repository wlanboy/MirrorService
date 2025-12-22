package com.wlanboy.mirrorservice.controller;

import java.util.concurrent.Executor;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;

@RestController
@RequestMapping("/mirror")
public class MirrorController {

    private final Executor taskExecutor;

    public MirrorController(@Qualifier("mirrorTaskExecutor") Executor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    @Operation(summary = "Mirror Service", description = "Spiegelt den Request basierend auf den Instruktionen.")
    @RequestMapping(method = { RequestMethod.GET})
    public DeferredResult<ResponseEntity<String>> mirrorGet(
            @ParameterObject MirrorInstruction instruction) {

        DeferredResult<ResponseEntity<String>> deferredResult = new DeferredResult<>(instruction.waitMs() + 5000L);

        taskExecutor.execute(() -> {
            try {
                if (instruction.waitMs() > 0) {
                    Thread.sleep(Math.min(instruction.waitMs(), 60000));
                }

                var response = ResponseEntity.status(instruction.statusCode());
                instruction.responseHeaders().forEach(response::header);

                deferredResult.setResult(response.body(instruction.responseBody()));
            } catch (Exception e) {
                deferredResult.setErrorResult(ResponseEntity.internalServerError().build());
            }
        });

        return deferredResult;
    }

    @Operation(summary = "Mirror Service", description = "Spiegelt den Request basierend auf den Instruktionen.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = "application/json", schema = @Schema(implementation = MirrorInstruction.class))))
    @RequestMapping(method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE })
    public DeferredResult<ResponseEntity<String>> mirror(@RequestBody MirrorInstruction instruction) {

        DeferredResult<ResponseEntity<String>> deferredResult = new DeferredResult<>(instruction.waitMs() + 5000L);

        taskExecutor.execute(() -> {
            try {
                if (instruction.waitMs() > 0) {
                    Thread.sleep(Math.min(instruction.waitMs(), 60000));
                }

                var response = ResponseEntity.status(instruction.statusCode());
                instruction.responseHeaders().forEach(response::header);

                deferredResult.setResult(response.body(instruction.responseBody()));
            } catch (Exception e) {
                deferredResult.setErrorResult(ResponseEntity.internalServerError().build());
            }
        });

        return deferredResult;
    }
}