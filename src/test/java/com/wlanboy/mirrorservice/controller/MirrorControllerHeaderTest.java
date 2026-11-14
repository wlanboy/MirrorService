package com.wlanboy.mirrorservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.wlanboy.mirrorservice.controller.AsyncMockMvc.perform;

/**
 * Schutz-Header und Sperre von Set-Cookie im Standardbetrieb (mirror.allow-set-cookie=false).
 */
@WebMvcTest(MirrorController.class)
class MirrorControllerHeaderTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testDefaultSecurityHeaders() throws Exception {
        perform(mockMvc, get("/mirror")
                .param("statusCode", "200")
                .param("waitMs", "0")
                .param("responseBody", "<script>alert(1)</script>")
                .param("responseHeaders[Content-Type]", "text/html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/html"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void testCallerOverridesSecurityHeaders() throws Exception {
        perform(mockMvc, post("/mirror")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "statusCode": 200,
                      "responseBody": "OK",
                      "waitMs": 0,
                      "responseHeaders": {
                        "content-security-policy": "default-src 'self'",
                        "x-content-type-options": "none"
                      }
                    }
                    """))
                .andExpect(status().isOk())
                .andExpect(header().stringValues("Content-Security-Policy", contains("default-src 'self'")))
                .andExpect(header().stringValues("X-Content-Type-Options", contains("none")));
    }

    @Test
    void testSetCookieBlockedByDefault() throws Exception {
        perform(mockMvc, post("/mirror")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "statusCode": 200,
                      "responseBody": "OK",
                      "waitMs": 0,
                      "responseHeaders": {
                        "Set-Cookie": "session=evil; Domain=.gmk.lan",
                        "X-Test": "kept"
                      }
                    }
                    """))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(header().string("X-Mirror-Blocked-Header", "Set-Cookie"))
                .andExpect(header().string("X-Test", "kept"));
    }

    @Test
    void testSetCookieBlockedCaseInsensitive() throws Exception {
        perform(mockMvc, get("/mirror")
                .param("statusCode", "200")
                .param("waitMs", "0")
                .param("responseHeaders[set-cookie]", "a=b"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(header().string("X-Mirror-Blocked-Header", "set-cookie"));
    }
}
