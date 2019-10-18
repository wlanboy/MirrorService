package com.wlanboy.mirrorservice.service;

import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

public class ProcessingTask extends TimerTask {

	private HttpEntity<String> request;
	private DeferredResult<ResponseEntity<String>> deferredResult;
	private Integer statuscode;
	private Integer wait;
	private Integer counter;

	private static final Logger logger = LoggerFactory.getLogger(ProcessingTask.class);

	private static final String REQUESTCOUNTER = "REQ_COUNTER";

	public ProcessingTask(HttpEntity<String> request, Integer statuscode,
			DeferredResult<ResponseEntity<String>> deferredResult, Integer wait, int requestcounter) {
		this.statuscode = statuscode;
		this.deferredResult = deferredResult;
		this.request = request;
		this.wait = wait;
		this.counter = requestcounter;
	}

	@Override
	public void run() {
		if (deferredResult.isSetOrExpired()) {
			logger.warn("Processing of non-blocking request already expired");
		} else {
			HttpHeaders headers = new HttpHeaders();
			headers.addAll(request.getHeaders());
			headers.add(REQUESTCOUNTER, Integer.toString(counter));

			ResponseEntity<String> response = new ResponseEntity<>(request.getBody(), headers,
					HttpStatus.resolve(statuscode));
			try {
				Thread.sleep(wait);
			} catch (InterruptedException e) {
				logger.error(e.getMessage());
			}
			boolean deferredStatus = deferredResult.setResult(response);
			logger.info("Processing of non-blocking request {} done, deferredStatus = {}", request, deferredStatus);
		}
	}
}
