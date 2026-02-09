package br.gov.go.saude.athena.loader;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Metadados do package.json de um package FHIR.
 */
public record PackageMetadata(
    String name,
    String version,
    @JsonProperty("tools-version") Integer toolsVersion,
    String type,
    String date,
    String license,
    String canonical,
    String url,
    String title,
    String description,
    List<String> fhirVersions,
    Map<String, String> dependencies,
    String author,
    List<Maintainer> maintainers,
    Directories directories,
    String jurisdiction
) {
    /**
     * Informações do mantenedor do package.
     */
    public record Maintainer(String name, String email, String url) {}

    public record Directories(String lib, String example) {}
}
