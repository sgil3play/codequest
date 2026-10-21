package com.codequest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada de CodeQuest.
 *
 * <p>Aplicacion de practica de programacion: catalogo de retos, ejecucion
 * del codigo en una JVM aislada y seguimiento del progreso.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CodeQuestApplication {

	public static void main(String[] args) {
		SpringApplication.run(CodeQuestApplication.class, args);
	}
}
