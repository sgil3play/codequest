import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerArray;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * Runner generado en tiempo de ejecucion y compilado junto al codigo del usuario.
 *
 * <p>Se apoya en la JUnit Platform Launcher API, pero NO imprime el formato de
 * consola de JUnit: emite un protocolo de lineas propio y estable
 * ("PASS|nombre", "FAIL|nombre|motivo", "SUMMARY|ok|fail|abort|skip") que el
 * backend parsea de forma trivial y sin depender del idioma ni del formato
 * ascii de JUnit.
 *
 * <p><b>Canal del protocolo.</b> El protocolo NO viaja por stdout. Se acumula
 * en memoria y, al terminar la suite, se escribe de una sola vez en el fichero
 * cuya ruta recibe el proceso en la propiedad
 * {@code codequest.protocol.file}. El motivo es que el codigo del usuario
 * corre en esta misma JVM y puede escribir directamente en
 * {@code FileDescriptor.out}: si el protocolo fuera por stdout, bastaria una
 * linea "PASS|..." falsa para falsear el veredicto. Con el fichero aparte, esa
 * escritura solo envenena stdout, que el backend ya no usa para el veredicto.
 *
 * <p>stdout se desvia a stderr antes de ejecutar los tests, de modo que
 * cualquier System.out.println del codigo del usuario no puede corromper nada
 * y se muestra en la consola del reto.
 */
public class Runner {

	private static final int MAX_REASON = 600;

	/** Propiedad del sistema con la ruta del fichero de protocolo. */
	private static final String PROTOCOL_PROPERTY = "codequest.protocol.file";

	public static void main(String[] args) throws Exception {
		// La ruta se lee y la propiedad se borra de inmediato: el codigo del
		// usuario no debe poder conocerla.
		String protocolPath = System.getProperty(PROTOCOL_PROPERTY);
		System.clearProperty(PROTOCOL_PROPERTY);

		// Todo lo que el codigo del usuario imprima sale por stderr.
		System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));

		// El protocolo se acumula en memoria y se escribe una sola vez al final.
		List<String> protocol = Collections.synchronizedList(new ArrayList<>());

		// pass, fail, abort, skip
		AtomicIntegerArray counters = new AtomicIntegerArray(4);

		// Fallos de contenedores (por ejemplo un @BeforeAll que lanza). Se guardan
		// aparte: un contenedor tambien se marca como fallido cuando un caso
		// suyo falla, y emitirlos siempre contaria cada fallo dos veces.
		List<String> containerFailures = Collections.synchronizedList(new ArrayList<>());

		if (args.length < 1) {
			protocol.add("SUMMARY|0|0|0|0|ERROR|no se indico la clase de test");
			write(protocolPath, protocol);
			return;
		}

		try {
			LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
					.selectors(DiscoverySelectors.selectClass(args[0]))
					.build();

			Launcher launcher = LauncherFactory.create();
			launcher.execute(request, new TestExecutionListener() {

				@Override
				public void executionFinished(TestIdentifier id, TestExecutionResult result) {
					if (!id.isTest()) {
						if (result.getStatus() == TestExecutionResult.Status.FAILED) {
							containerFailures.add("FAIL|" + escape(id.getDisplayName()) + "|"
									+ escape(reason(result)));
						}
						return;
					}
					String name = escape(id.getDisplayName());
					TestExecutionResult.Status status = result.getStatus();
					switch (status) {
						case SUCCESSFUL -> {
							counters.incrementAndGet(0);
							protocol.add("PASS|" + name);
						}
						case ABORTED -> {
							counters.incrementAndGet(2);
							protocol.add("ABORTED|" + name + "|" + escape(reason(result)));
						}
						default -> {
							counters.incrementAndGet(1);
							protocol.add("FAIL|" + name + "|" + escape(reason(result)));
						}
					}
				}

				@Override
				public void executionSkipped(TestIdentifier id, String reason) {
					if (!id.isTest()) {
						return;
					}
					counters.incrementAndGet(3);
					protocol.add("SKIPPED|" + escape(id.getDisplayName()) + "|" + escape(reason));
				}
			});
		}
		catch (Throwable t) {
			protocol.add("ERROR|" + escape(String.valueOf(t)));
		}

		int total = counters.get(0) + counters.get(1) + counters.get(2) + counters.get(3);

		// Si no llego a ejecutarse ni un caso, el motivo esta en el contenedor
		// (por ejemplo un @BeforeAll que lanza). Sin esto, el backend solo
		// podria decir "no se ha ejecutado ningun caso".
		if (total == 0 && !containerFailures.isEmpty()) {
			for (String failure : containerFailures) {
				protocol.add(failure);
				counters.incrementAndGet(1);
			}
		}

		protocol.add("SUMMARY|" + counters.get(0) + "|" + counters.get(1) + "|" + counters.get(2) + "|"
				+ counters.get(3));

		write(protocolPath, protocol);
	}

	/**
	 * Escribe el protocolo acumulado en el fichero indicado.
	 *
	 * <p>Se escribe de una sola vez y truncando el fichero: el codigo del
	 * usuario podria haber escrito ahi durante la ejecucion, pero lo que
	 * importa es lo que el runner escribio al final.
	 */
	private static void write(String path, List<String> lines) {
		if (path == null || path.isBlank()) {
			return;
		}
		try {
			String content = String.join("\n", lines);
			Files.writeString(Path.of(path), content, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
		}
		catch (IOException e) {
			// Si no se puede escribir, el backend veria que falta el protocolo.
			System.err.println("No se pudo escribir el protocolo: " + e);
		}
	}

	private static String reason(TestExecutionResult result) {
		// Desde JUnit Platform 1.10, getThrowable() devuelve Optional<Throwable>.
		return result.getThrowable()
				.map(t -> {
					String message = t.getMessage();
					String type = t.getClass().getName();
					return message == null ? type : type + ": " + message;
				})
				.orElse("");
	}

	private static String escape(String value) {
		if (value == null) {
			return "";
		}
		String flat = value.replace("\\", "\\\\")
				.replace("|", "\\|")
				.replace("\r", " ")
				.replace("\n", " ")
				.trim();
		return flat.length() > MAX_REASON ? flat.substring(0, MAX_REASON) + "..." : flat;
	}
}
