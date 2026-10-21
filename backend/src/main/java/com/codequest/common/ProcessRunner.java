package com.codequest.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Utilidad para lanzar un proceso externo, leer su salida sin deadlocks y
 * cortarlo si se pasa del tiempo limite.
 *
 * <p>Los dos flujos se consumen en hilos independientes: si se leiese
 * stdout de forma sincrona y el proceso llenase stderr, este se quedaria
 * bloqueado y el proceso nunca terminaria.
 */
public final class ProcessRunner {

	private ProcessRunner() {
	}

	/**
	 * @param command  comando y argumentos
	 * @param workingDir directorio de trabajo del proceso
	 * @param timeoutSeconds limite de tiempo; si se supera, el proceso se destruye
	 * @param maxOutputBytes tope de bytes leidos de cada flujo
	 */
	public static Result run(List<String> command, java.io.File workingDir, int timeoutSeconds, int maxOutputBytes)
			throws IOException, InterruptedException {

		ProcessBuilder builder = new ProcessBuilder(command);
		builder.directory(workingDir);
		builder.redirectErrorStream(false);

		Process process = builder.start();

		CompletableFuture<String> stdout = readAsync(process.getInputStream(), maxOutputBytes);
		CompletableFuture<String> stderr = readAsync(process.getErrorStream(), maxOutputBytes);

		boolean timedOut = false;
		int exitCode = -1;
		if (process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
			exitCode = process.exitValue();
		}
		else {
			// Se agoto el limite: se destruye el proceso para que no se quede
			// consumiendo CPU en segundo plano.
			timedOut = true;
			process.destroyForcibly();
			process.waitFor(5, TimeUnit.SECONDS);
		}

		return new Result(
				exitCode,
				stdout.getNow(""),
				stderr.getNow(""),
				timedOut);
	}

	private static CompletableFuture<String> readAsync(InputStream stream, int maxBytes) {
		return CompletableFuture.supplyAsync(() -> {
			try (InputStream in = stream) {
				byte[] buffer = new byte[8192];
				java.io.ByteArrayOutputStream bufferOut = new java.io.ByteArrayOutputStream();
				int read;
				int total = 0;
				while ((read = in.read(buffer)) != -1) {
					int remaining = maxBytes - total;
					if (remaining <= 0) {
						break;
					}
					int toWrite = Math.min(read, remaining);
					bufferOut.write(buffer, 0, toWrite);
					total += toWrite;
				}
				return bufferOut.toString(StandardCharsets.UTF_8);
			}
			catch (IOException e) {
				return "";
			}
		});
	}

	/**
	 * @param exitCode  codigo de salida, o -1 si el proceso fue destruido
	 * @param stdout    salida estandar
	 * @param stderr    salida de error
	 * @param timedOut  true si se supero el limite de tiempo
	 */
	public record Result(int exitCode, String stdout, String stderr, boolean timedOut) {
	}
}
