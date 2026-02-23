package com.wlanboy.mirrorservice.controller;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.stereotype.Component;

@Component
public class DnsResolver {

    public InetAddress[] getAllByName(String hostname) throws UnknownHostException {
        return InetAddress.getAllByName(hostname);
    }

    public InetAddress getByName(String hostname) throws UnknownHostException {
        return InetAddress.getByName(hostname);
    }

    public boolean isReachable(InetAddress address, int timeoutMs) throws IOException {
        return address.isReachable(timeoutMs);
    }
}
