package br.gov.go.saude.athena.loader;

/**
 * Representa a origem de um package FHIR.
 */
public interface PackageSource {
    
    /**
     * Carrega o conteúdo do package.
     * 
     * @return conteúdo do package em bytes
     * @throws Exception se houver erro no carregamento
     */
    byte[] load() throws Exception;
    
    /**
     * Retorna o identificador do package.
     */
    String getPackageId();
    
    /**
     * Retorna a versão do package.
     */
    String getVersion();
}
