package com.codequest.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codequest.design.DesignCatalog;
import com.codequest.domain.Track;
import com.codequest.kata.KataCatalog;
import com.codequest.progress.dto.ProgressSummary;
import com.codequest.quiz.QuizCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test de integracion del resumen de progreso.
 *
 * <p>Comprueba que los totales por pista cuadran con el catalogo y que un
 * elemento superado cuenta como completado. Estos numeros son los que se
 * ensenan en el panel de la aplicacion, asi que un desajuste aqui se ve
 * directamente en la interfaz.
 */
@SpringBootTest
@Transactional
class ProgressSummaryTest {

	@Autowired
	private ProgressService progressService;

	@Autowired
	private KataCatalog kataCatalog;

	@Autowired
	private QuizCatalog quizCatalog;

	@Autowired
	private DesignCatalog designCatalog;

	@Test
	@DisplayName("El total por pista no cuenta dos veces los elementos intentados")
	void elTotalPorPistaNoSeDuplica() {
		// Un intento fallido crea la fila de progreso: el total de la pista no
		// debe crecer por ello, porque el elemento ya estaba en el catalogo.
		progressService.recordAttempt("test", "java-optional-001", Track.JAVA_CORE, 0.0, 10, false);

		ProgressSummary summary = progressService.getSummary("test");

		ProgressSummary.TrackProgress pista = pista(summary, Track.JAVA_CORE);
		assertEquals(elementosEn(Track.JAVA_CORE), pista.total(), "El total de la pista no coincide con el catalogo");
		assertEquals(0, pista.completed());
	}

	@Test
	@DisplayName("Un reto superado cuenta como completado y suma XP")
	void unRetoSuperadoCuenta() {
		progressService.recordAttempt("test", "java-optional-001", Track.JAVA_CORE, 1.0, 10, true);

		ProgressSummary summary = progressService.getSummary("test");

		ProgressSummary.TrackProgress pista = pista(summary, Track.JAVA_CORE);
		assertEquals(1, pista.completed());
		assertEquals(elementosEn(Track.JAVA_CORE), pista.total());
		assertEquals(10, summary.totalXp());
		assertEquals(1, summary.completedItems());
	}

	@Test
	@DisplayName("Un intento fallido posterior no borra el completado")
	void elCompletadoNoSeBorra() {
		progressService.recordAttempt("test", "java-optional-001", Track.JAVA_CORE, 1.0, 10, true);
		progressService.recordAttempt("test", "java-optional-001", Track.JAVA_CORE, 0.0, 10, false);

		ProgressSummary summary = progressService.getSummary("test");
		assertEquals(1, summary.completedItems());
		assertEquals(1, pista(summary, Track.JAVA_CORE).completed());
		// El XP se cobra una sola vez, aunque se repita el reto.
		assertEquals(10, summary.totalXp());
	}

	@Test
	@DisplayName("El total global coincide con el numero de elementos del catalogo")
	void elTotalGlobalCoincide() {
		ProgressSummary summary = progressService.getSummary("test");
		assertTrue(summary.totalItems() > 0);
		assertEquals(summary.totalItems(),
				summary.byTrack().stream().mapToInt(ProgressSummary.TrackProgress::total).sum());
	}

	/** Numero de elementos del catalogo que pertenecen a una pista. */
	private long elementosEn(Track track) {
		return kataCatalog.findByTrack(track).size()
				+ quizCatalog.findAll().stream().filter(q -> q.track() == track).count()
				+ designCatalog.findAll().stream().filter(d -> d.track() == track).count();
	}

	private ProgressSummary.TrackProgress pista(ProgressSummary summary, Track track) {
		return summary.byTrack().stream()
				.filter(t -> t.track().equals(track.name()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("La pista " + track + " no aparece en el resumen"));
	}
}
