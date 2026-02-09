<?xml version="1.0" encoding="UTF-8"?>
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
  <sch:ns prefix="f" uri="http://hl7.org/fhir"/>
  <sch:ns prefix="h" uri="http://www.w3.org/1999/xhtml"/>
  <!-- 
    This file contains just the constraints for the profile Attachment
    It includes the base constraints for the resource as well.
    Because of the way that schematrons and containment work, 
    you may need to use this schematron fragment to build a, 
    single schematron that validates contained resources (if you have any) 
  -->
  <sch:pattern>
    <sch:title>f:Attachment</sch:title>
    <sch:rule context="f:Attachment">
      <sch:assert test="count(f:extension[@url = 'https://fhir.saude.go.gov.br/r4/seguranca/StructureDefinition/hash-sha256']) &gt;= 1">extension with URL = 'https://fhir.saude.go.gov.br/r4/seguranca/StructureDefinition/hash-sha256': minimum cardinality of 'extension' is 1</sch:assert>
      <sch:assert test="count(f:extension[@url = 'https://fhir.saude.go.gov.br/r4/seguranca/StructureDefinition/hash-sha256']) &lt;= 1">extension with URL = 'https://fhir.saude.go.gov.br/r4/seguranca/StructureDefinition/hash-sha256': maximum cardinality of 'extension' is 1</sch:assert>
      <sch:assert test="count(f:contentType) &gt;= 1">contentType: minimum cardinality of 'contentType' is 1</sch:assert>
      <sch:assert test="count(f:size) &gt;= 1">size: minimum cardinality of 'size' is 1</sch:assert>
      <sch:assert test="count(f:hash) &gt;= 1">hash: minimum cardinality of 'hash' is 1</sch:assert>
    </sch:rule>
  </sch:pattern>
</sch:schema>
