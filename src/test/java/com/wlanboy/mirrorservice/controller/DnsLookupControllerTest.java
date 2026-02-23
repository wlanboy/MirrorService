package com.wlanboy.mirrorservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DnsLookupController.class)
class DnsLookupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DnsResolver dnsResolver;

    // ---------------------------------------------------------
    // DNS Resolve
    // ---------------------------------------------------------

    @Test
    void testResolveDns_success() throws Exception {
        InetAddress addr = InetAddress.getByName("93.184.216.34");
        when(dnsResolver.getAllByName("example.com")).thenReturn(new InetAddress[]{addr});

        var result = mockMvc.perform(get("/resolve/example.com"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0]").value("93.184.216.34"));
    }

    @Test
    void testResolveDns_multipleAddresses() throws Exception {
        InetAddress addr1 = InetAddress.getByName("93.184.216.34");
        InetAddress addr2 = InetAddress.getByName("2606:2800:21f:cb07:6820:80da:af6b:8b2c");
        when(dnsResolver.getAllByName("example.com")).thenReturn(new InetAddress[]{addr1, addr2});

        var result = mockMvc.perform(get("/resolve/example.com"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testResolveDns_unknownHost() throws Exception {
        when(dnsResolver.getAllByName("unknown.example")).thenThrow(new UnknownHostException("unknown.example"));

        var result = mockMvc.perform(get("/resolve/unknown.example"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$[0]").value("Hostname 'unknown.example' konnte nicht aufgelöst werden."));
    }

    // ---------------------------------------------------------
    // Ping
    // ---------------------------------------------------------

    @Test
    void testPing_success() throws Exception {
        InetAddress addr = InetAddress.getByName("93.184.216.34");
        when(dnsResolver.getByName("example.com")).thenReturn(addr);
        when(dnsResolver.isReachable(addr, 1000)).thenReturn(true);

        var result = mockMvc.perform(get("/ping/example.com"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hostname").value("example.com"))
                .andExpect(jsonPath("$.resolvedIp").value("93.184.216.34"))
                .andExpect(jsonPath("$.reachable").value(true))
                .andExpect(jsonPath("$.responseTimeMs").isNumber());
    }

    @Test
    void testPing_timeout() throws Exception {
        InetAddress addr = InetAddress.getByName("192.0.2.1");
        when(dnsResolver.getByName("192.0.2.1")).thenReturn(addr);
        when(dnsResolver.isReachable(addr, 1000)).thenReturn(false);

        var result = mockMvc.perform(get("/ping/192.0.2.1"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isRequestTimeout())
                .andExpect(jsonPath("$.reachable").value(false));
    }

    @Test
    void testPing_unknownHost() throws Exception {
        when(dnsResolver.getByName("unknown.example")).thenThrow(new UnknownHostException("unknown.example"));

        var result = mockMvc.perform(get("/ping/unknown.example"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.hostname").value("unknown.example"))
                .andExpect(jsonPath("$.reachable").value(false));
    }

    @Test
    void testPing_withCustomTimeout() throws Exception {
        InetAddress addr = InetAddress.getByName("93.184.216.34");
        when(dnsResolver.getByName("example.com")).thenReturn(addr);
        when(dnsResolver.isReachable(addr, 2000)).thenReturn(true);

        var result = mockMvc.perform(get("/ping/example.com").param("timeoutMs", "2000"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reachable").value(true));
    }
}
