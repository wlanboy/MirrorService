package com.wlanboy.mirrorservice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HelloController {
    @Autowired
    private BuildProperties buildProperties;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("serviceName", buildProperties.getName());
        model.addAttribute("serviceVersion", buildProperties.getVersion());
        return "index";
    }  
}
