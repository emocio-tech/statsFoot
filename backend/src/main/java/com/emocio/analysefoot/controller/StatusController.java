package com.emocio.analysefoot.controller;

import com.emocio.analysefoot.model.StatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StatusController {

    @GetMapping("/status")
    public StatusResponse status() {
        return new StatusResponse("analyse-foot", "UP");
    }
}
