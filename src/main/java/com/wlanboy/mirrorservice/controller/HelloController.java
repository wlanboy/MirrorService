package com.wlanboy.mirrorservice.controller;

import java.util.Optional;

import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Controller
@Tag(name = "Home", description = "Startseiten-Endpunkt")
public class HelloController {

    private final Optional<BuildProperties> buildProperties;

    public HelloController(Optional<BuildProperties> buildProperties) {
        this.buildProperties = buildProperties;
    }

    @Operation(summary = "Startseite", description = "Zeigt die Startseite mit Service-Informationen an.")
    @ApiResponse(responseCode = "200", description = "Startseite erfolgreich geladen")
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("serviceName", buildProperties.map(BuildProperties::getName).orElse("MirrorService"));
        model.addAttribute("serviceVersion", buildProperties.map(BuildProperties::getVersion).orElse("unknown"));
        return "index";
    }
}
