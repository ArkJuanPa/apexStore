package com.apexstore.contratos;
import java.nio.file.*;
public final class Entorno {
 private Entorno(){}
 public static String valor(String key,String fallback){String value=System.getenv(key);if(value!=null)return value;Path file=Path.of(".env");if(Files.isRegularFile(file)){try{for(String line:Files.readAllLines(file)){String s=line.trim();if(s.startsWith("#")||!s.startsWith(key+"="))continue;value=s.substring(s.indexOf('=')+1).trim();if(value.length()>1&&((value.startsWith("\"")&&value.endsWith("\""))||(value.startsWith("'")&&value.endsWith("'"))))value=value.substring(1,value.length()-1);return value;}}catch(Exception e){throw new IllegalStateException("No se pudo leer .env",e);}}return fallback;}
}
