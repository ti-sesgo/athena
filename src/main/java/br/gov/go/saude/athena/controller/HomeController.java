package br.gov.go.saude.athena.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Serve a página de entrada (landing page) na raiz do servidor.
 */
@RestController
@ConditionalOnProperty(name = "athena.landing-page.enabled", havingValue = "true", matchIfMissing = false)
public class HomeController {

    private static final String INDEX_PATH = "static/index.html";

    @GetMapping(value = { "", "/" }, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> home() throws IOException {
        ClassPathResource resource = new ClassPathResource(INDEX_PATH);
        String html = resource.getContentAsString(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }
}
