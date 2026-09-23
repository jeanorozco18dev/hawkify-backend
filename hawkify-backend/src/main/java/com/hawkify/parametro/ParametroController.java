package com.hawkify.parametro;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Politicas y textos legales que el frontend muestra a cualquier visitante. */
@RestController
@RequiredArgsConstructor
public class ParametroController {

    private final ParametroService service;

    @GetMapping("/api/parametros/publicos")
    public Map<String, String> publicos() {
        return service.publicos();
    }
}
