package com.codequest.kata;

import com.codequest.common.ProcessRunner;
import com.codequest.common.config.RunnerProperties;
import com.codequest.kata.dto.SubmissionResult;
import com.codequest.kata.dto.TestCaseResult;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Motor de ejecucion de codigo.
 *
 * <p>Flujo de una entrega:
 * <ol>
 *   <li>Se crea un directorio temporal aislado.</li>
 *   <li>Se escriben tres ficheros: el codigo de la persona, la clase de test
 *       del reto y un runner generado que habla un protocolo propio.</li>
 *   <li>Se compila con {@code javac} en una JVM HIJA (nunca en el proceso de
 *       Spring, para que un bucle infinito o un {@code System.exit} no tumben
 *       el servidor).</li>
 *   <li>Se ejecuta la suite con timeout y se parsea el protocolo de salida.</li>
 * </ol>
 *
 * <p><b>Limitacion conocida:</b> el aislamiento es de proceso y de tiempo, no de
 * seguridad. El codigo se ejecuta con los permisos del usuario. Es aceptable
 * para una herramienta local de practica; para exponerla a terceros haria falta
 * un contenedor sin privilegios o un perfil de Java con limites del sistema.
 */
@Component
public class KataRunner {

	private static final Logger log = LoggerFactory.getLogger(KataRunner.class);
	private static final String RUNNER_RESOURCE = "runner/Runner.java";
	private static final int CHILD_HEAP_MB = 256;

	private final RunnerProperties props;
	private final String runnerSource;
	private String junitConsoleJar;

	public KataRunner(RunnerProperties props) {
		this.props = props;
		this.runnerSource = readResource(RUNNER_RESOURCE);
	}

	@PostConstruct
	void resolveJunitJar() {
		this.junitConsoleJar = locateJunitConsoleJar();
		log.info("Runner de codigo inicializado. JUnit console standalone: {}", junitConsoleJar);
	}

	/**
	 * Compila y ejecuta la solucion de la persona contra la suite del reto.
	 *
	 * @param kata     reto a evaluar
	 * @param userCode codigo fuente entregado
	 * @return veredicto con el detalle de cada caso de test
	 */
	public SubmissionResult run(KataDefinition kata, String userCode) {
		long startedAt = System.currentTimeMillis();
		Path workspace = null;
		try {
			workspace = Files.createTempDirectory(Paths.get(props.workDir()), "codequest-");
			Path classesDir = workspace.resolve("classes");
			Files.createDirectories(classesDir);

			Path solutionFile = workspace.resolve(kata.solutionClass() + ".java");
			Path testFile = workspace.resolve(kata.testClass() + ".java");
			Path runnerFile = workspace.resolve("Runner.java");

			write(solutionFile, userCode);
			write(testFile, kata.testSource());
			write(runnerFile, runnerSource);

			ProcessRunner.Result compile = compile(solutionFile, testFile, runnerFile, classesDir, workspace);
			if (compile.timedOut()) {
				return result(kata, SubmissionResult.Status.COMPILATION_ERROR, List.of(), 0, 0,
						"Se ha agotado el tiempo de compilacion", null, null, startedAt);
			}
			if (compile.exitCode() != 0) {
				return result(kata, SubmissionResult.Status.COMPILATION_ERROR, List.of(), 0, 0,
						"El codigo no compila", compile.stderr(), null, startedAt);
			}

			ProcessRunner.Result execution = execute(classesDir, kata, workspace);
			if (execution.timedOut()) {
				return result(kata, SubmissionResult.Status.TIMEOUT, List.of(), 0, 0,
						"Se ha agotado el tiempo limite", null, execution.stderr(), startedAt);
			}

			return interpret(kata, execution, startedAt);
		}
		catch (IOException | InterruptedException | RuntimeException ex) {
			log.warn("Fallo ejecutando el reto {}: {}", kata.id(), ex.toString());
			if (ex instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return result(kata, SubmissionResult.Status.RUNTIME_ERROR, List.of(), 0, 0,
					"Error interno al ejecutar el codigo: " + ex.getMessage(), null, null, startedAt);
		}
		finally {
			deleteRecursively(workspace);
		}
	}

	private ProcessRunner.Result compile(Path solution, Path test, Path runner, Path classesDir, Path workspace)
			throws IOException, InterruptedException {

		List<String> command = List.of(
				javaExecutable("javac"),
				"-encoding", "UTF-8",
				"-nowarn",
				"-cp", junitConsoleJar,
				"-d", classesDir.toString(),
				solution.toString(),
				test.toString(),
				runner.toString());

		return ProcessRunner.run(command, workspace.toFile(), props.compileTimeoutSeconds(), props.maxOutputBytes());
	}

	private ProcessRunner.Result execute(Path classesDir, KataDefinition kata, Path workspace)
			throws IOException, InterruptedException {

		String classPath = classesDir + java.io.File.pathSeparator + junitConsoleJar;

		List<String> command = List.of(
				javaExecutable("java"),
				"-Xmx" + CHILD_HEAP_MB + "m",
				"-XX:+UseSerialGC",
				"-Dfile.encoding=UTF-8",
				// Limite por caso: JUnit interrumpe el test cuando se supera y
				// lo marca como abortado. Es lo que permite que un bucle infinito
				// se corte en segundos en lugar de agotar el limite global.
				"-Djunit.jupiter.execution.timeout.default=" + kata.timeLimitSeconds() + "s",
				"-cp", classPath,
				"Runner",
				kata.testClass());

		// El limite global es la red de seguridad: si JUnit no puede ni
		// interrumpir el test (por ejemplo, un bucle que ignora la interrupcion),
		// el proceso hijo se mata igualmente.
		int totalTimeoutSeconds = Math.max(30, kata.timeLimitSeconds() * 4);

		return ProcessRunner.run(command, workspace.toFile(), totalTimeoutSeconds, props.maxOutputBytes());
	}

	private SubmissionResult interpret(KataDefinition kata, ProcessRunner.Result execution, long startedAt) {
		List<TestCaseResult> cases = new ArrayList<>();
		int passed = 0;
		int failed = 0;

		for (String line : execution.stdout().split("\\R")) {
			if (line.isBlank()) {
				continue;
			}
			List<String> parts = splitEscaped(line);
			String keyword = parts.get(0);
			switch (keyword) {
				case "PASS" -> {
					passed++;
					cases.add(new TestCaseResult(unescape(parts.get(1)), TestCaseResult.TestStatus.PASSED, null));
				}
				case "FAIL" -> {
					failed++;
					String message = parts.size() > 2 ? unescape(parts.get(2)) : "";
					cases.add(new TestCaseResult(unescape(parts.get(1)), TestCaseResult.TestStatus.FAILED, message));
				}
				case "ABORTED" -> {
					failed++;
					String message = parts.size() > 2 ? unescape(parts.get(2)) : "";
					cases.add(new TestCaseResult(unescape(parts.get(1)), TestCaseResult.TestStatus.ABORTED, message));
				}
				case "SKIPPED" -> cases.add(new TestCaseResult(unescape(parts.get(1)),
						TestCaseResult.TestStatus.SKIPPED, parts.size() > 2 ? unescape(parts.get(2)) : null));
				default -> {
					// "SUMMARY" y "ERROR" se tratan fuera del bucle.
				}
			}
		}

		int total = cases.size();
		double score = total == 0 ? 0 : (double) passed / total;
		String programOutput = execution.stderr();

		if (total == 0) {
			String message = execution.stdout().contains("ERROR|")
					? "La suite de test no se pudo ejecutar: " + execution.stdout().lines()
							.filter(l -> l.startsWith("ERROR|"))
							.map(l -> unescape(l.substring(6)))
							.findFirst().orElse("motivo desconocido")
					: "No se ha ejecutado ningun caso de test. Revisa que el metodo tenga la firma esperada.";
			return result(kata, SubmissionResult.Status.RUNTIME_ERROR, cases, 0, 0, message, null, programOutput,
					startedAt);
		}

		// Un caso abortado por el limite de tiempo se reporta como TIMEOUT: para
		// quien esta practicando, "se te ha pasado el tiempo" es mucho mas util
		// que "un test ha fallado".
		boolean agotado = cases.stream()
				.anyMatch(c -> c.status() == TestCaseResult.TestStatus.ABORTED
						&& c.message() != null
						&& c.message().toLowerCase().contains("timed out"));

		if (agotado) {
			return result(kata, SubmissionResult.Status.TIMEOUT, cases, passed, total,
					"Se ha agotado el tiempo limite de " + kata.timeLimitSeconds() + " s", null, programOutput,
					startedAt);
		}

		SubmissionResult.Status status = failed == 0 ? SubmissionResult.Status.PASSED : SubmissionResult.Status.TESTS_FAILED;
		String message = failed == 0
				? "Todos los casos pasan. Enhorabuena."
				: failed + " de " + total + " casos fallan.";

		return result(kata, status, cases, passed, total, message, null, programOutput, startedAt);
	}

	private SubmissionResult result(KataDefinition kata, SubmissionResult.Status status, List<TestCaseResult> cases,
			int passed, int total, String message, String compilerOutput, String programOutput, long startedAt) {
		double score = total == 0 ? 0 : (double) passed / total;
		return new SubmissionResult(
				kata.id(),
				status,
				List.copyOf(cases),
				passed,
				total,
				score,
				compilerOutput,
				programOutput,
				message,
				System.currentTimeMillis() - startedAt);
	}

	/* --------------------------- utilidades --------------------------- */

	private String javaExecutable(String name) {
		String configured = "java".equals(name) ? props.javaExecutable() : props.javacExecutable();
		if (configured != null && !configured.isBlank()) {
			return configured;
		}
		String home = System.getProperty("java.home");
		if (home != null) {
			Path candidate = Paths.get(home, "bin", name + (isWindows() ? ".exe" : ""));
			if (Files.isExecutable(candidate)) {
				return candidate.toString();
			}
		}
		return name;
	}

	private boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase().contains("win");
	}

	private String locateJunitConsoleJar() {
		if (props.junitConsoleJar() != null && !props.junitConsoleJar().isBlank()) {
			Path configured = Paths.get(props.junitConsoleJar());
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

	private String readResource(String resource) {
		try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalStateException("Falta el recurso " + resource);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("No se pudo leer " + resource, ex);
		}
	}

	private void write(Path path, String content) throws IOException {
		Files.writeString(path, content == null ? "" : content, StandardCharsets.UTF_8);
	}

	private void deleteRecursively(Path path) {
		if (path == null || !Files.exists(path)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(path)) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				}
				catch (IOException ignored) {
					// Si no se puede borrar, el directorio temporal se limpia solo.
				}
			});
		}
		catch (IOException ignored) {
			// idem
		}
	}

	/** Divide una linea del protocolo respetando los escapes de barra vertical. */
	private List<String> splitEscaped(String line) {
		List<String> parts = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (int i = 0; i < line.length(); i++) {
			char ch = line.charAt(i);
			if (ch == '\\' && i + 1 < line.length()) {
				char next = line.charAt(i + 1);
				if (next == '|' || next == '\\') {
					current.append(next);
					i++;
					continue;
				}
			}
			if (ch == '|') {
				parts.add(current.toString());
				current.setLength(0);
				continue;
			}
			current.append(ch);
		}
		parts.add(current.toString());
		return parts;
	}

	private String unescape(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\|", "|").replace("\\\\", "\\");
	}
}
