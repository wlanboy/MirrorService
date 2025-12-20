package com.wlanboy.mirrorservice.controller;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
@RequestMapping("/mirror")
public class MirrorController {

    private static final Logger log = LoggerFactory.getLogger(MirrorController.class);
    private static final AtomicInteger counter = new AtomicInteger(0);
    private final Executor taskExecutor;

    public MirrorController(@Qualifier("mirrorTaskExecutor") Executor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    @RequestMapping(method = { RequestMethod.GET, RequestMethod.POST })
    public DeferredResult<ResponseEntity<String>> mirror(
            HttpEntity<String> httpEntity, // Für den rohen POST-Body
            @RequestParam(value = "request", required = false) String requestParam,
            @RequestParam(value = "statuscode", defaultValue = "200") Integer statuscode,
            @RequestParam(value = "wait", defaultValue = "0") Integer wait) {

        int requestId = counter.incrementAndGet();
        int effectiveWait = Math.min(wait, 60000);
        DeferredResult<ResponseEntity<String>> deferredResult = new DeferredResult<>(effectiveWait + 5000L);

        taskExecutor.execute(() -> {
            try {
                if (effectiveWait > 0)
                    Thread.sleep(effectiveWait);

                String contentToReturn;
                if (requestParam != null) {
                    contentToReturn = requestParam;
                } else {
                    contentToReturn = httpEntity.getBody() != null ? httpEntity.getBody() : "";
                }

                deferredResult.setResult(ResponseEntity
                        .status(statuscode)
                        .header("X-Request-ID", String.valueOf(requestId))
                        .header("Content-Type", "text/plain; charset=UTF-8")
                        .body(contentToReturn));

                log.info("[ID: {}] Mirrored content length: {}", requestId, contentToReturn.length());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                deferredResult.setErrorResult(ResponseEntity.internalServerError().build());
            }
        });

        return deferredResult;
    }
}