package com.codequest.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros del motor de ejecucion de codigo.
 *
 * @param timeoutSeconds       limite por caso de test (la JVM del usuario no puede colgarse)
 * @param compileTimeoutSeconds limite de la compilacion
 * @param maxOutputBytes       tope de salida capturada, para no reventar la memoria
 * @param workDir              directorio raiz de los workspaces temporales
 * @param javaExecutable       ejecutable {@code java}; si se deja vacio se usa el del propio proceso
 * @param javacExecutable      ejecutable {@code javac}; si se deja vacio se usa el del propio proceso
 * @param junitConsoleJar      ruta al jar standalone de JUnit; si se deja vacio se localiza solo
 */
@ConfigurationProperties(prefix = "codequest.runner")
public record RunnerProperties(
		int timeoutSeconds,
		int compileTimeoutSeconds,
		int maxOutputBytes,
		String workDir,
		String javaExecutable,
		String javacExecutable,
		String junitConsoleJar) {

	public RunnerProperties {
		if (timeoutSeconds <= 0) {
			timeoutSeconds = 5;
		}
		if (compileTimeoutSeconds <= 0) {
			compileTimeoutSeconds = 20;
		}
		if (maxOutputBytes <= 0) {
			maxOutputBytes = 256 * 1024;
		}
		if (workDir == null || workDir.isBlank()) {
			workDir = System.getProperty("java.io.tmpdir");
		}
	}
}
