import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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
 * <p>stdout se desvia a stderr antes de ejecutar los tests, de modo que cualquier
 * System.out.println del codigo del usuario no puede corromper el protocolo.
 */
public class Runner {

	private static final int MAX_REASON = 600;

	public static void main(String[] args) throws Exception {
		PrintStream protocol = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);

		// Todo lo que el codigo del usuario imprima sale por stderr.
		System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));

		if (args.length < 1) {
			protocol.println("SUMMARY|0|0|0|0|ERROR|no se indico la clase de test");
			protocol.flush();
			return;
		}

		int[] counters = new int[] {0, 0, 0, 0}; // pass, fail, abort, skip

		try {
			LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
					.selectors(DiscoverySelectors.selectClass(args[0]))
					.build();

			Launcher launcher = LauncherFactory.create();
			launcher.execute(request, new TestExecutionListener() {

				@Override
				public void executionFinished(TestIdentifier id, TestExecutionResult result) {
					if (!id.isTest()) {
						return;
					}
					String name = escape(id.getDisplayName());
					TestExecutionResult.Status status = result.getStatus();
					switch (status) {
						case SUCCESSFUL -> {
							counters[0]++;
							protocol.println("PASS|" + name);
						}
						case ABORTED -> {
							counters[2]++;
							protocol.println("ABORTED|" + name + "|" + escape(reason(result)));
						}
						default -> {
							counters[1]++;
							protocol.println("FAIL|" + name + "|" + escape(reason(result)));
						}
					}
				}

				@Override
				public void executionSkipped(TestIdentifier id, String reason) {
					if (!id.isTest()) {
						return;
					}
					counters[3]++;
					protocol.println("SKIPPED|" + escape(id.getDisplayName()) + "|" + escape(reason));
				}
			});
		}
		catch (Throwable t) {
			protocol.println("ERROR|" + escape(String.valueOf(t)));
		}

		protocol.println("SUMMARY|" + counters[0] + "|" + counters[1] + "|" + counters[2] + "|" + counters[3]);
		protocol.flush();
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
