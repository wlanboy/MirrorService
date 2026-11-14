package com.wlanboy.mirrorservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.wlanboy.mirrorservice.controller.AsyncMockMvc.perform;

@WebMvcTest(controllers = MirrorController.class, properties = "mirror.allow-set-cookie=true")
class MirrorControllerSetCookieOptInTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testSetCookiePassedThroughWhenAllowed() throws Exception {
        perform(mockMvc, get("/mirror")
                .param("statusCode", "200")
                .param("waitMs", "0")
                .param("responseHeaders[Set-Cookie]", "session=test"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", "session=test"))
                .andExpect(header().doesNotExist("X-Mirror-Blocked-Header"));
    }
}
