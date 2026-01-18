package com.wlanboy.mirrorservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

@RestController
@Tag(name = "DNS Lookup", description = "DNS-Auflösungs-Endpunkte")
public class DnsLookupController {

    @Operation(
        summary = "DNS-Auflösung durchführen",
        description = "Führt eine lokale DNS-Auflösung für den angegebenen Hostnamen durch und gibt die gefundenen IP-Adressen zurück."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "DNS-Auflösung erfolgreich"),
        @ApiResponse(responseCode = "404", description = "Hostname konnte nicht aufgelöst werden"),
        @ApiResponse(responseCode = "403", description = "DNS-Auflösung wegen Sicherheitseinschränkungen verweigert"),
        @ApiResponse(responseCode = "500", description = "Unerwarteter Fehler")
    })
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
