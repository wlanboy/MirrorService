package com.wlanboy.mirrorservice.controller;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.ForwardedHeaderFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EchoController.class)
class EchoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testEchoPost() throws Exception {
        mockMvc.perform(post("/echo/api/orders")
                .queryParam("a", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Test", "one", "two")
                .content("{\"hello\": \"world\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.method").value("POST"))
                .andExpect(jsonPath("$.path").value("/echo/api/orders"))
                .andExpect(jsonPath("$.query").value("a=1"))
                .andExpect(jsonPath("$.url").value("http://localhost/echo/api/orders"))
                .andExpect(jsonPath("$.headers.X-Test", contains("one", "two")))
                .andExpect(jsonPath("$.body").value("{\"hello\": \"world\"}"))
                .andExpect(jsonPath("$.bodyBytes").value(18))
                .andExpect(jsonPath("$.bodyTruncated").value(false));
    }

    @Test
    void testEchoRootPathWithoutBody() throws Exception {
        mockMvc.perform(get("/echo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("GET"))
                .andExpect(jsonPath("$.path").value("/echo"))
                .andExpect(jsonPath("$.query").value(nullValue()))
                .andExpect(jsonPath("$.body").value(""))
                .andExpect(jsonPath("$.bodyBytes").value(0));
    }

    @Test
    void testEchoMasksCredentials() throws Exception {
        mockMvc.perform(get("/echo")
                .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.secret")
                .header("Proxy-Authorization", "secret-without-scheme")
                .header("Cookie", "session=abc123; theme=dark"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headers.Authorization", contains("Bearer ***")))
                .andExpect(jsonPath("$.headers.Proxy-Authorization", contains("***")))
                .andExpect(jsonPath("$.headers.Cookie", contains("session=***; theme=***")));
    }

    @Test
    void testEchoTruncatesLargeBody() throws Exception {
        byte[] body = new byte[EchoController.MAX_BODY_BYTES + 100];
        Arrays.fill(body, (byte) 'x');
        mockMvc.perform(put("/echo/upload")
                .contentType(MediaType.TEXT_PLAIN)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bodyBytes").value(EchoController.MAX_BODY_BYTES))
                .andExpect(jsonPath("$.bodyTruncated").value(true));
    }

    @Test
    void testEchoShowsForwardedHeadersAndPeerAddress() throws Exception {
        // Wie in Produktion mit forward-headers-strategy=framework: der Filter wertet
        // X-Forwarded-* aus und blendet die Header danach aus dem Request aus.
        var forwarded = MockMvcBuilders.standaloneSetup(new EchoController())
                .addFilters(new ForwardedHeaderFilter())
                .build();

        forwarded.perform(get("/echo/app")
                .with(request -> {
                    request.setRemoteAddr("10.42.0.12");
                    return request;
                })
                .header("X-Forwarded-For", "203.0.113.7")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "mirror.example.com")
                .header("X-Forwarded-Port", "443"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remoteAddress").value("203.0.113.7"))
                .andExpect(jsonPath("$.peerAddress").value("10.42.0.12"))
                .andExpect(jsonPath("$.url").value("https://mirror.example.com/echo/app"))
                .andExpect(jsonPath("$.headers.X-Forwarded-For", contains("203.0.113.7")))
                .andExpect(jsonPath("$.headers.X-Forwarded-Proto", contains("https")));
    }

    @Test
    void testMaskCookieWithoutValue() {
        assertThat(EchoController.mask("cookie", "flag; a=1;")).isEqualTo("***; a=***");
    }
}
