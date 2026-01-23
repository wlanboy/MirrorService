package com.wlanboy.mirrorservice.controller;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MirrorController.class)
class MirrorControllerNegTest {

    @Autowired
    private MockMvc mockMvc;

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
    // 8 — DELETE ohne Body → erwartet 400 Bad Request (fehlender RequestBody)
    // ---------------------------------------------------------
    @Test
    void testDeleteWithoutBody() throws Exception {
        mockMvc.perform(delete("/mirror"))
                .andExpect(status().isBadRequest());
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

    // ---------------------------------------------------------
    // 10 — Validierung: statusCode < 100
    // ---------------------------------------------------------
    @Test
    void testStatusCodeTooLow() throws Exception {
        mockMvc.perform(post("/mirror")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "statusCode": 99,
                      "responseBody": "X",
                      "waitMs": 0,
                      "responseHeaders": {}
                    }
                    """))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------
    // 11 — Validierung: statusCode > 599
    // ---------------------------------------------------------
    @Test
    void testStatusCodeTooHigh() throws Exception {
        mockMvc.perform(post("/mirror")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "statusCode": 600,
                      "responseBody": "X",
                      "waitMs": 0,
                      "responseHeaders": {}
                    }
                    """))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------
    // 12 — Validierung: waitMs > 60000
    // ---------------------------------------------------------
    @Test
    void testWaitMsTooHigh() throws Exception {
        mockMvc.perform(post("/mirror")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "statusCode": 200,
                      "responseBody": "X",
                      "waitMs": 60001,
                      "responseHeaders": {}
                    }
                    """))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------
    // 13 — statusCode=0 wird durch Record-Default auf 200 gesetzt
    // (Jackson → Record-Konstruktor → dann Validierung)
    // ---------------------------------------------------------
    @Test
    void testStatusCodeZeroBecomesDefault() throws Exception {
        var result = mockMvc.perform(post("/mirror")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "statusCode": 0,
                      "responseBody": "DEFAULT",
                      "waitMs": 0,
                      "responseHeaders": {}
                    }
                    """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string("DEFAULT"));
    }

    // ---------------------------------------------------------
    // 14 — GET ohne Parameter → Validierungsfehler
    // ---------------------------------------------------------
    @Test
    void testGetWithoutParams() throws Exception {
        mockMvc.perform(get("/mirror"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------
    // 15 — responseBody = null
    // ---------------------------------------------------------
    @Test
    void testNullResponseBody() throws Exception {
        var result = mockMvc.perform(post("/mirror")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "statusCode": 200,
                      "responseBody": null,
                      "waitMs": 0,
                      "responseHeaders": {}
                    }
                    """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }
}
