package com.wlanboy.mirrorservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.Executor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MirrorController.class)
@Import(MirrorControllerTest.TestConfig.class)
class MirrorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class TestConfig {

        @Bean
        public Executor mirrorTaskExecutor() {
            return Runnable::run;
        }
    }

    // ---------------------------------------------------------
    // GET
    // ---------------------------------------------------------
    @Test
    void testMirrorGet() throws Exception {
        var result = mockMvc.perform(get("/mirror")
                .param("statusCode", "200")
                .param("responseBody", "GET-OK")
                .param("waitMs", "0"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string("GET-OK"));
    }

    // ---------------------------------------------------------
    // POST
    // ---------------------------------------------------------
    @Test
    void testMirrorPost() throws Exception {
        String json = """
            {
              "statusCode": 201,
              "responseBody": "POST-OK",
              "waitMs": 0,
              "responseHeaders": {
                "X-Test": "POST"
              }
            }
            """;

        var result = mockMvc.perform(post("/mirror")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Test", "POST"))
                .andExpect(content().string("POST-OK"));
    }

    // ---------------------------------------------------------
    // PUT
    // ---------------------------------------------------------
    @Test
    void testMirrorPut() throws Exception {
        String json = """
            {
              "statusCode": 202,
              "responseBody": "PUT-OK",
              "waitMs": 0,
              "responseHeaders": {
                "X-Mode": "PUT"
              }
            }
            """;

        var result = mockMvc.perform(put("/mirror")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Mode", "PUT"))
                .andExpect(content().string("PUT-OK"));
    }

    // ---------------------------------------------------------
    // DELETE
    // ---------------------------------------------------------
    @Test
    void testMirrorDelete() throws Exception {
        String json = """
            {
              "statusCode": 204,
              "responseBody": "",
              "waitMs": 0,
              "responseHeaders": {
                "X-Deleted": "true"
              }
            }
            """;

        var result = mockMvc.perform(delete("/mirror")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isNoContent())
                .andExpect(header().string("X-Deleted", "true"))
                .andExpect(content().string(""));
    }
}
