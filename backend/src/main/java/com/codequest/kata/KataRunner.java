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
import java.util.concurrent.Semaphore;
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
 *   <li>Se ejecuta la suite con timeout y se parsea el protocolo.</li>
 * </ol>
 *
 * <p><b>Canal del protocolo.</b> El protocolo de resultados no viaja por
 * stdout, sino por un fichero temporal propio que el proceso hijo recibe en la
 * propiedad {@code codequest.protocol.file}. El codigo de la persona corre en
 * esa misma JVM y puede escribir en {@code FileDescriptor.out}, asi que usar
 * stdout como canal de veredicto permitiria anteponer lineas "PASS|" falsas.
 * El backend comprueba ademas que los contadores del protocolo cuadren con las
 * lineas leidas, de modo que un protocolo manipulado se detecta.
 *
 * <p><b>Limitacion conocida:</b> el aislamiento es de proceso y de tiempo, no de
 * seguridad. El codigo se ejecuta con los permisos del usuario y puede leer el
 * sistema de ficheros. Es aceptable para una herramienta local de practica;
 * para exponerla a terceros haria falta un contenedor sin privilegios o un
 * perfil de Java con limites del sistema.
 */
@Component
public class KataRunner {

	private static final Logger log = LoggerFactory.getLogger(KataRunner.class);
	private static final String RUNNER_RESOURCE = "runner/Runner.java";
	private static final int CHILD_HEAP_MB = 256;
	private static final int CHILD_METASPACE_MB = 64;
	private static final int CHILD_CODE_CACHE_MB = 32;

	/**
	 * Tope de ejecuciones simultaneas. Cada entrega lanza un {@code javac} y una
	 * JVM hija, asi que sin este limite unas cuantas peticiones en paralelo
	 * pueden dejar el host sin CPU.
	 */
	private final Semaphore executions;

	private final RunnerProperties props;
	private final String runnerSource;
	private String junitConsoleJar;

	public KataRunner(RunnerProperties props) {
		this.props = props;
		this.executions = new Semaphore(props.maxConcurrent());
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

		// Cada entrega lanza un javac y una JVM hija. Sin este tope, N
		// peticiones simultaneas = N cores al 100% durante el limite global.
		if (!executions.tryAcquire()) {
			log.warn("Se rechazan {} entregas simultaneas: hay {} en curso", kata.id(),
					props.maxConcurrent());
			return result(kata, SubmissionResult.Status.RUNTIME_ERROR, List.of(), 0, 0,
					"Hay demasiadas ejecuciones en curso. Intentalo de nuevo en unos segundos.",
					null, null, startedAt);
		}
		try {
			return runInternal(kata, userCode, startedAt);
		}
		finally {
			executions.release();
		}
	}

	private SubmissionResult runInternal(KataDefinition kata, String userCode, long startedAt) {
		Path workspace = null;
		Path protocolFile = null;
		try {
			workspace = Files.createTempDirectory(Paths.get(props.workDir()), "codequest-");
			Path classesDir = workspace.resolve("classes");
			Files.createDirectories(classesDir);

			Path solutionFile = resolveInside(workspace, kata.solutionClass() + ".java");
			Path testFile = resolveInside(workspace, kata.testClass() + ".java");
			Path runnerFile = resolveInside(workspace, "Runner.java");

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
						"El codigo no compila", sanitize(compile.stderr(), workspace), null, startedAt);
			}

			protocolFile = Files.createTempFile(Paths.get(props.workDir()), "codequest-protocol-", ".tmp");
			ProcessRunner.Result execution = execute(classesDir, kata, workspace, protocolFile);
			if (execution.timedOut()) {
				return result(kata, SubmissionResult.Status.TIMEOUT, List.of(), 0, 0,
						"Se ha agotado el tiempo limite", null, execution.stderr(), startedAt);
			}

			return interpret(kata, readProtocol(protocolFile), execution, startedAt);
		}
		catch (IOException | InterruptedException | RuntimeException ex) {
			log.warn("Fallo ejecutando el reto {}: {}", kata.id(), ex.toString());
			if (ex instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return result(kata, SubmissionResult.Status.RUNTIME_ERROR, List.of(), 0, 0,
					"Error interno al ejecutar el codigo: " + sanitize(ex.getMessage(), workspace),
					null, null, startedAt);
		}
		finally {
			deleteRecursively(workspace);
			deleteQuietly(protocolFile);
		}
	}

	/**
	 * Compila el codigo de la persona junto con la suite del reto.
	 *
	 * <p>Los ficheros se pasan con el nombre relativo y el directorio de trabajo
	 * del proceso es el workspace: asi la salida del compilador dice
	 * "Solution.java:3: error: ..." y no la ruta absoluta del servidor, que
	 * filtraria el nombre de usuario del sistema.
	 */
	private ProcessRunner.Result compile(Path solution, Path test, Path runner, Path classesDir, Path workspace)
			throws IOException, InterruptedException {

		List<String> command = List.of(
				javaExecutable("javac"),
				// El compilador tambien es un proceso hijo: se acota igual que
				// la JVM de ejecucion para que no se coma la memoria del host.
				"-J-Xmx" + CHILD_HEAP_MB + "m",
				"-J-XX:MaxRAM=" + CHILD_HEAP_MB + "m",
				"-J-XX:MaxMetaspaceSize=" + CHILD_METASPACE_MB + "m",
				"-encoding", "UTF-8",
				"-nowarn",
				"-cp", junitConsoleJar,
				"-d", classesDir.getFileName().toString(),
				solution.getFileName().toString(),
				test.getFileName().toString(),
				runner.getFileName().toString());

		return ProcessRunner.run(command, workspace.toFile(), props.compileTimeoutSeconds(), props.maxOutputBytes());
	}

	private ProcessRunner.Result execute(Path classesDir, KataDefinition kata, Path workspace, Path protocolFile)
			throws IOException, InterruptedException {

		String classPath = classesDir + java.io.File.pathSeparator + junitConsoleJar;

		// Limite por caso. El kata puede fijar el suyo; si no, se usa el valor
		// por defecto de la configuracion.
		int perCase = kata.timeLimitSeconds() > 0 ? kata.timeLimitSeconds() : props.defaultTimeoutSeconds();

		List<String> command = List.of(
				javaExecutable("java"),
				"-Xmx" + CHILD_HEAP_MB + "m",
				"-XX:MaxRAM=" + CHILD_HEAP_MB + "m",
				"-XX:MaxMetaspaceSize=" + CHILD_METASPACE_MB + "m",
				"-XX:ReservedCodeCacheSize=" + CHILD_CODE_CACHE_MB + "m",
				// Una sola CPU para la JVM hija: el paralelismo no aporta nada a
				// un reto y multiplica el consumo en paralelo.
				"-XX:ActiveProcessorCount=1",
				"-XX:+UseSerialGC",
				// Si el codigo agota la memoria, la JVM hija muere en lugar de
				// quedarse swappeando hasta que la mate el padre.
				"-XX:+ExitOnOutOfMemoryError",
				"-Dfile.encoding=UTF-8",
				// Limite por caso: JUnit interrumpe el test cuando se supera y lo
				// marca como abortado.
				"-Djunit.jupiter.execution.timeout.default=" + perCase + "s",
				// Canal del protocolo: un fichero propio, no stdout.
				"-Dcodequest.protocol.file=" + protocolFile.toAbsolutePath(),
				"-cp", classPath,
				"Runner",
				kata.testClass());

		// El limite global es la red de seguridad: si JUnit no puede ni
		// interrumpir el test (por ejemplo, un bucle que ignora la interrupcion),
		// el proceso hijo se mata igualmente.
		int totalTimeoutSeconds = Math.max(props.globalTimeoutSeconds(), perCase * 3);

		return ProcessRunner.run(command, workspace.toFile(), totalTimeoutSeconds, props.maxOutputBytes());
	}

	private SubmissionResult interpret(KataDefinition kata, String protocol, ProcessRunner.Result execution,
			long startedAt) {

		List<TestCaseResult> cases = new ArrayList<>();
		int passed = 0;
		int failed = 0;
		int aborted = 0;
		int skipped = 0;
		int[] summary = null;
		String error = null;

		if (protocol != null) {
			for (String line : protocol.split("\\R")) {
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
						aborted++;
						String message = parts.size() > 2 ? unescape(parts.get(2)) : "";
						cases.add(new TestCaseResult(unescape(parts.get(1)), TestCaseResult.TestStatus.ABORTED, message));
					}
					case "SKIPPED" -> {
						skipped++;
						cases.add(new TestCaseResult(unescape(parts.get(1)),
								TestCaseResult.TestStatus.SKIPPED, parts.size() > 2 ? unescape(parts.get(2)) : null));
					}
					case "SUMMARY" -> summary = parseSummary(parts);
					case "ERROR" -> error = parts.size() > 1 ? unescape(parts.get(1)) : "motivo desconocido";
					default -> {
						// Lineas que no pertenecen al protocolo: se ignoran.
					}
				}
			}
		}

		int total = cases.size();
		String programOutput = execution.stderr();

		// El protocolo tiene que terminar con SUMMARY. Si no lo hace, la suite
		// no llego a su fin: lo mas probable es que el codigo haya terminado la
		// JVM (System.exit) o que el proceso haya muerto.
		if (summary == null) {
			String message = (protocol == null || protocol.isBlank())
					? "La ejecucion se ha interrumpido antes de terminar: el codigo ha terminado la JVM "
							+ "(por ejemplo, con System.exit) o el proceso ha muerto antes de escribir el resultado."
					: "La ejecucion se ha interrumpido antes de terminar. Los casos que llegaron a ejecutarse "
							+ "son los de abajo, pero el resultado no es fiable.";
			return result(kata, SubmissionResult.Status.RUNTIME_ERROR, cases, passed, total, message, null,
					programOutput, startedAt);
		}

		// Los contadores del protocolo deben cuadrar con las lineas leidas. Si no
		// cuadran, el protocolo ha sido manipulado o esta corrupto.
		if (summary[0] != passed || summary[1] != failed || summary[2] != aborted || summary[3] != skipped) {
			log.warn("Protocolo de ejecucion incoherente en el reto {}: contadores {} y lineas leidas {}", kata.id(),
					java.util.Arrays.toString(summary), (passed + failed + aborted + skipped));
			return result(kata, SubmissionResult.Status.RUNTIME_ERROR, cases, passed, total,
					"El resultado de la ejecucion no es coherente y no se puede interpretar de forma fiable.",
					null, programOutput, startedAt);
		}

		if (total == 0) {
			String message = error != null
					? "La suite de test no se pudo ejecutar: " + error
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
			int perCase = kata.timeLimitSeconds() > 0 ? kata.timeLimitSeconds() : props.defaultTimeoutSeconds();
			return result(kata, SubmissionResult.Status.TIMEOUT, cases, passed, total,
					"Se ha agotado el tiempo limite de " + perCase + " s", null, programOutput,
					startedAt);
		}

		// PASSED solo si todos los casos han pasado. Un caso omitido cuenta como
		// total, asi que "no ha fallado ninguno" no es lo mismo que "ha pasado
		// todo".
		boolean todoPasa = total > 0 && passed == total;
		SubmissionResult.Status status = todoPasa
				? SubmissionResult.Status.PASSED
				: SubmissionResult.Status.TESTS_FAILED;
		String message = todoPasa
				? "Todos los casos pasan. Enhorabuena."
				: (failed + aborted) + " de " + total + " casos fallan.";

		return result(kata, status, cases, passed, total, message, null, programOutput, startedAt);
	}

	/** Lee los cuatro contadores de la linea SUMMARY. */
	private int[] parseSummary(List<String> parts) {
		int[] counters = new int[] {-1, -1, -1, -1};
		for (int i = 0; i < 4 && i + 1 < parts.size(); i++) {
			try {
				counters[i] = Integer.parseInt(parts.get(i + 1).trim());
			}
			catch (NumberFormatException ex) {
				log.debug("Contador de protocolo no numerico: {}", parts.get(i + 1));
			}
		}
		return counters;
	}

	private String readProtocol(Path protocolFile) {
		if (protocolFile == null || !Files.isRegularFile(protocolFile)) {
			return null;
		}
		try {
			return Files.readString(protocolFile, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			log.warn("No se pudo leer el protocolo de ejecucion: {}", ex.toString());
			return null;
		}
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

	/**
	 * Resuelve un nombre de fichero dentro del workspace y comprueba que no se
	 * sale. Es la segunda linea de defensa: el catalogo ya valida que los
	 * nombres de clase sean identificadores Java, pero el runner no debe
	 * escribir fuera del directorio temporal pase lo que pase con el catalogo.
	 */
	private Path resolveInside(Path workspace, String fileName) {
		Path resolved = workspace.resolve(fileName).normalize();
		if (!resolved.startsWith(workspace)) {
			throw new IllegalStateException("El fichero '" + fileName + "' queda fuera del workspace");
		}
		return resolved;
	}

	/**
	 * Sustituye la ruta del workspace por un marcador.
	 *
	 * <p>El compilador y la JVM pueden escribir rutas absolutas del servidor en
	 * sus mensajes. Esas rutas revelan el nombre de usuario del sistema y la
	 * estructura de directorios, asi que se sustituyen antes de devolverlas.
	 */
	private String sanitize(String text, Path workspace) {
		if (text == null || text.isBlank()) {
			return text;
		}
		String sanitized = text;
		if (workspace != null) {
			sanitized = sanitized.replace(workspace.toAbsolutePath().toString(), "<workspace>");
			sanitized = sanitized.replace(workspace.toString(), "<workspace>");
		}
		return sanitized;
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

	private void deleteQuietly(Path path) {
		if (path == null) {
			return;
		}
		try {
			Files.deleteIfExists(path);
		}
		catch (IOException ex) {
			log.debug("No se pudo borrar el fichero de protocolo: {}", ex.toString());
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
