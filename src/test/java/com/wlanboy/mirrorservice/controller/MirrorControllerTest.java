package com.wlanboy.mirrorservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.wlanboy.mirrorservice.controller.AsyncMockMvc.perform;

@WebMvcTest(MirrorController.class)
class MirrorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ---------------------------------------------------------
    // GET
    // ---------------------------------------------------------
    @Test
    void testMirrorGet() throws Exception {
        perform(mockMvc, get("/mirror")
                .param("statusCode", "200")
                .param("responseBody", "GET-OK")
                .param("waitMs", "0"))
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

        perform(mockMvc, post("/mirror")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
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

        perform(mockMvc, put("/mirror")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
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

        perform(mockMvc, delete("/mirror")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isNoContent())
                .andExpect(header().string("X-Deleted", "true"))
                .andExpect(content().string(""));
    }

    // ---------------------------------------------------------
    // PATCH
    // ---------------------------------------------------------
    @Test
    void testMirrorPatch() throws Exception {
        String json = """
            {
              "statusCode": 200,
              "responseBody": "PATCH-OK",
              "waitMs": 0,
              "responseHeaders": {
                "X-Method": "PATCH"
              }
            }
            """;

        perform(mockMvc, patch("/mirror")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Method", "PATCH"))
                .andExpect(content().string("PATCH-OK"));
    }

    // ---------------------------------------------------------
    // waitMs Delay
    // ---------------------------------------------------------
    @Test
    void testMirrorWithDelay() throws Exception {
        String json = """
            {
              "statusCode": 200,
              "responseBody": "DELAYED",
              "waitMs": 100,
              "responseHeaders": {}
            }
            """;

        long start = System.currentTimeMillis();

        perform(mockMvc, post("/mirror")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(content().string("DELAYED"));

        long elapsed = System.currentTimeMillis() - start;
        assert elapsed >= 100 : "Delay should be at least 100ms, was " + elapsed + "ms";
    }
}
