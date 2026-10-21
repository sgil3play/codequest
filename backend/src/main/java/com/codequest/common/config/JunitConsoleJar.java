package com.codequest.common.config;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Localiza el jar standalone de JUnit Platform.
 *
 * <p>Lo usan dos componentes que necesitan la misma clase en el classpath: el
 * runner, que compila y ejecuta la solucion de la persona, y el servicio de
 * autocompletado, que analiza el codigo con el compilador del JDK y quiere
 * resolver tambien las clases de JUnit.
 *
 * <p>La busqueda tiene tres pasos, del mas determinista al mas tolerante:
 * <ol>
 *   <li>La ruta configurada en {@code codequest.runner.junit-console-jar}.</li>
 *   <li>El jar que copia el plugin de Maven a {@code target/}.</li>
 *   <li>El propio uber-jar del repositorio local de Maven, localizado por una
 *       clase que solo existe en el.</li>
 * </ol>
 */
public final class JunitConsoleJar {

	private static final Logger log = LoggerFactory.getLogger(JunitConsoleJar.class);

	private JunitConsoleJar() {
	}

	/**
	 * Devuelve la ruta al jar standalone de JUnit.
	 *
	 * @param configuredPath ruta opcional configurada por properties
	 * @return ruta absoluta o relativa al jar encontrado
	 * @throws IllegalStateException si no se encuentra el jar
	 */
	public static String locate(String configuredPath) {
		if (configuredPath != null && !configuredPath.isBlank()) {
			Path configured = Paths.get(configuredPath);
			if (Files.isRegularFile(configured)) {
				return configured.toString();
			}
			log.warn("codequest.runner.junit-console-jar no existe: {}", configured);
		}

		// 1) El jar que copia maven-dependency-plugin a target/. Es la via
		//    determinista: no depende del orden del classpath.
		Path target = Paths.get(System.getProperty("user.dir"), "target", "junit-platform-console-standalone.jar");
		if (Files.isRegularFile(target)) {
			return target.toString();
		}

		// 2) El propio jar standalone del repositorio local de Maven.
		//    Se localiza por una clase que SOLO existe en ese uber-jar
		//    (ConsoleLauncher), no por LauncherFactory: esa clase tambien la
		//    aporta spring-boot-starter-test y su code source puede no ser el
		//    uber-jar, lo que dejaria al codigo del usuario sin las clases de
		//    JUnit Jupiter en el classpath.
		try {
			Class<?> marker = Class.forName("org.junit.platform.console.ConsoleLauncher");
			URL location = marker.getProtectionDomain().getCodeSource().getLocation();
			if (location != null) {
				Path path = Paths.get(location.toURI());
				if (Files.isRegularFile(path)) {
					return path.toString();
				}
			}
		}
		catch (URISyntaxException | RuntimeException | ClassNotFoundException ex) {
			log.debug("No se pudo localizar el jar de JUnit via code source", ex);
		}

		throw new IllegalStateException(
				"No se encuentra junit-platform-console-standalone.jar. Ejecuta 'mvn generate-resources' "
						+ "o ajusta codequest.runner.junit-console-jar.");
	}
}
