package br.gov.go.saude.athena.util;


import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

@Slf4j
public class Util {

    public static byte[] loadResource(String resourceName) throws IOException {
        try {
            return new ClassPathResource(resourceName).getInputStream().readAllBytes();
        } catch (IOException e) {
            log.error("Resource not found: {}", resourceName);
            throw e;
        }
    }
}
