package com.apexstore.pruebas;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArquitecturaTest {
 @Test void nodoTresNoAccedeABaseDeDatos(){
  ArchRule rule=noClasses().that().resideInAPackage("com.apexstore.nodo3..").should().dependOnClassesThat().resideInAnyPackage("java.sql..","org.postgresql..","com.zaxxer..");
  rule.check(new ClassFileImporter().importPackages("com.apexstore.nodo3"));
 }
 @Test void contextoNoDependeDeIceNiEstrategiasConcretas(){
  ArchRule rule=noClasses().that().haveFullyQualifiedName("com.apexstore.nodo2.ProcesadorPagosContexto").should().dependOnClassesThat().resideInAnyPackage("com.zeroc..","com.apexstore.ice..","com.apexstore.nodo3..","com.apexstore.estrategias..","com.apexstore.nodo2.Estrategia*");
  rule.check(new ClassFileImporter().importPackages("com.apexstore.nodo2"));
 }
}
