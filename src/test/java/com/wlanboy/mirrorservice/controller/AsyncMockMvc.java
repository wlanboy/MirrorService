package com.wlanboy.mirrorservice.controller;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

final class AsyncMockMvc {

    private AsyncMockMvc() {
    }

    static ResultActions perform(MockMvc mockMvc, RequestBuilder request) throws Exception {
        var result = mockMvc.perform(request)
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(result));
    }
}
