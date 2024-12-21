package com.wlanboy.mirrorservice.controller;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import com.wlanboy.mirrorservice.service.ProcessingTask;

@RestController
public class MirrorController {

    static AtomicInteger counter = new AtomicInteger(0);
    
    /**
     * http://127.0.0.1:8001/mirror
     * @param name String
     * @return String template
     */
    @PostMapping(value = "/mirror")
    public DeferredResult<ResponseEntity<String>> mirrorPost(HttpEntity<String> request, 
            @RequestParam(value = "statuscode", defaultValue = "200", required = false) Integer statuscode,
            @RequestParam(value = "wait", defaultValue = "0", required = false) Integer wait) {

        // Create the deferredResult and initiate a callback object, task, with it
        DeferredResult<ResponseEntity<String>> deferredResult = createDeferredResult(request, statuscode, wait);
        
        // Return to let go of the precious thread we are holding on to...
        return deferredResult;
    }

    @GetMapping(value = "/mirror")
    public DeferredResult<ResponseEntity<String>> mirrorGet(HttpEntity<String> request, 
            @RequestParam(value = "statuscode", defaultValue = "200", required = false) Integer statuscode,
            @RequestParam(value = "wait", defaultValue = "0", required = false) Integer wait) {
    
        // Create the deferredResult and initiate a callback object, task, with it
        DeferredResult<ResponseEntity<String>> deferredResult = createDeferredResult(request, statuscode, wait);
    
        return deferredResult;
    }

    private DeferredResult<ResponseEntity<String>> createDeferredResult(HttpEntity<String> request, Integer statuscode, Integer wait) {
        DeferredResult<ResponseEntity<String>> deferredResult = new DeferredResult<>();
        ProcessingTask task = new ProcessingTask(request, statuscode, deferredResult, wait, counter.incrementAndGet());
    
        task.run();
        return deferredResult;
    }
}
