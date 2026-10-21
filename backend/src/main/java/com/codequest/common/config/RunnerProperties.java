package com.codequest.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros del motor de ejecucion de codigo.
 *
 * @param defaultTimeoutSeconds limite por caso cuando el reto no fija el suyo
 * @param compileTimeoutSeconds  limite de la compilacion
 * @param globalTimeoutSeconds   limite global de la suite; es la red de seguridad
 *                               cuando el codigo ignora la interrupcion
 * @param maxConcurrent          numero de ejecuciones simultaneas permitidas
 * @param maxOutputBytes         tope de salida capturada, para no reventar la memoria
 * @param workDir                directorio raiz de los workspaces temporales
 * @param javaExecutable         ejecutable {@code java}; si se deja vacio se usa el del propio proceso
 * @param javacExecutable        ejecutable {@code javac}; si se deja vacio se usa el del propio proceso
 * @param junitConsoleJar        ruta al jar standalone de JUnit; si se deja vacio se localiza solo
 */
@ConfigurationProperties(prefix = "codequest.runner")
public record RunnerProperties(
		int defaultTimeoutSeconds,
		int compileTimeoutSeconds,
		int globalTimeoutSeconds,
		int maxConcurrent,
		int maxOutputBytes,
		String workDir,
		String javaExecutable,
		String javacExecutable,
		String junitConsoleJar) {

	public RunnerProperties {
		if (defaultTimeoutSeconds <= 0) {
			defaultTimeoutSeconds = 5;
		}
		if (compileTimeoutSeconds <= 0) {
			compileTimeoutSeconds = 20;
		}
		if (globalTimeoutSeconds <= 0) {
			globalTimeoutSeconds = 60;
		}
		if (maxConcurrent <= 0) {
			maxConcurrent = 4;
		}
		if (maxOutputBytes <= 0) {
			maxOutputBytes = 256 * 1024;
		}
		if (workDir == null || workDir.isBlank()) {
			workDir = System.getProperty("java.io.tmpdir");
		}
	}
}
