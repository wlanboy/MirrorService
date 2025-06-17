package com.wlanboy.mirrorservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

@RestController
public class DnsLookupController {
    
    /**
     * Performs a local DNS resolution for the given hostname
     * and returns the found IP addresses.
     *
     * @param hostname The hostname for which the DNS resolution should be performed.
     * @return A ResponseEntity containing a list of IP addresses (as String) on success,
     * or an error message on failure.
     */
    @GetMapping("/resolve/{hostname}")
    public ResponseEntity<List<String>> resolveDns(@PathVariable String hostname) {
        List<String> ipAddresses = new ArrayList<>();
        try {
            // Performs the DNS resolution
            InetAddress[] addresses = InetAddress.getAllByName(hostname);

            if (addresses.length > 0) {
                for (InetAddress address : addresses) {
                    ipAddresses.add(address.getHostAddress());
                }
                return new ResponseEntity<>(ipAddresses, HttpStatus.OK);
            } else {
                return new ResponseEntity<>(List.of("No IP addresses found for hostname " + hostname + "."), HttpStatus.NOT_FOUND);
            }

        } catch (UnknownHostException e) {
            // The hostname could not be resolved
            return new ResponseEntity<>(List.of("Hostname " + hostname + " could not be resolved."), HttpStatus.NOT_FOUND);
        } catch (SecurityException e) {
            // If there are security restrictions (e.g., due to a SecurityManager)
            return new ResponseEntity<>(List.of("DNS resolution error due to security restrictions: " + e.getMessage()), HttpStatus.FORBIDDEN);
        } catch (Exception e) {
            // General error handling
            return new ResponseEntity<>(List.of("An unexpected error occurred: " + e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
