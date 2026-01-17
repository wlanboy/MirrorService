package com.wlanboy.mirrorservice.controller;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.Executor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MirrorController.class)
@Import(MirrorControllerNegTest.TestConfig.class)
class MirrorControllerNegTest {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public Executor mirrorTaskExecutor() {
            return Runnable::run; // synchroner Executor
        }
    }

    // ---------------------------------------------------------
    // 3 — Negative waitMs
    // ---------------------------------------------------------
    @Test
    void testNegativeWaitMs() throws Exception {

        var result = mockMvc.perform(post("/mirror")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "statusCode": 200,
                      "responseBody": "OK",
                      "waitMs": -100,
                      "responseHeaders": {}
                    }
                    """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    // ---------------------------------------------------------
    // 7 — Verschiedene Statuscodes
    // ---------------------------------------------------------
    @Nested
    class StatusCodeTests {

        @Test void test100() throws Exception { testStatus(100); }
        @Test void test204() throws Exception { testStatus(204); }
        @Test void test301() throws Exception { testStatus(301); }
        @Test void test418() throws Exception { testStatus(418); }
        @Test void test500() throws Exception { testStatus(500); }

        private void testStatus(int code) throws Exception {
            var result = mockMvc.perform(post("/mirror")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "statusCode": %d,
                          "responseBody": "X",
                          "waitMs": 0,
                          "responseHeaders": {}
                        }
                        """.formatted(code)))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            mockMvc.perform(asyncDispatch(result))
                    .andExpect(status().is(code))
                    .andExpect(content().string("X"));
        }
    }

    // ---------------------------------------------------------
    // 8 — DELETE ohne Body
    // ---------------------------------------------------------
    @Test
    void testDeleteWithoutBody() throws Exception {

        var result = mockMvc.perform(delete("/mirror"))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Dein Controller validiert nicht → 200 OK ist korrekt
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------
    // 9 — GET mit komplexen Query-Parametern
    // ---------------------------------------------------------
    @Test
    void testGetWithComplexQueryParams() throws Exception {

        var result = mockMvc.perform(get("/mirror")
                .param("statusCode", "200")
                .param("responseBody", "OK")
                .param("waitMs", "0")
                .param("responseHeaders[X-Test]", "123")
                .param("responseHeaders[X-Mode]", "GET"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Test", "123"))
                .andExpect(header().string("X-Mode", "GET"))
                .andExpect(content().string("OK"));
    }
}
