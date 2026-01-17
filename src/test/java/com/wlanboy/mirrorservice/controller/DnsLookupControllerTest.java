package com.wlanboy.mirrorservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DnsLookupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testResolveDns_success() throws Exception {
        mockMvc.perform(get("/resolve/google.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void testResolveDns_unknownHost() throws Exception {
        mockMvc.perform(get("/resolve/this-host-does-not-exist-12345.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$[0]").value("Hostname this-host-does-not-exist-12345.com could not be resolved."));
    }
}

