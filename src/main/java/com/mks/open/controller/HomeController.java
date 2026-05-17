package com.mks.open.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    @Operation(summary = "Application check", description = "Returns application health status")
    public String home() {
        return "URL Shortener Backend Running";
    }
}