package com.codequest.progress;

import com.codequest.domain.Track;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Progreso sobre un elemento concreto del catalogo (kata, pregunta o ejercicio).
 *
 * <p>La clave primaria es compuesta por usuario y elemento, de modo que el
 * progreso es unico por combinacion y se puede consultar de forma indexada.
 * Se declara con {@link EmbeddedId} y no con dos campos {@code @Id}: la clase
 * de clave es un record, que ya aporta un {@code equals} y un {@code hashCode}
 * correctos, cosa que Hibernate exige para las claves compuestas.
 */
@Entity
@Table(name = "item_progress", indexes = {
		@Index(name = "idx_item_progress_username", columnList = "username"),
		@Index(name = "idx_item_progress_item", columnList = "item_id")
})
public class ItemProgress {

	public enum Status {
		NOT_STARTED,
		IN_PROGRESS,
		COMPLETED
	}

	@EmbeddedId
	private ItemProgressId id;

	@Enumerated(EnumType.STRING)
	@Column(name = "track", nullable = false, length = 32)
	private Track track;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private Status status = Status.NOT_STARTED;

	@Column(name = "attempts", nullable = false)
	private int attempts;

	/** Mejor puntuacion obtenida (0..1). */
	@Column(name = "best_score", nullable = false)
	private double bestScore;

	@Column(name = "xp_earned", nullable = false)
	private int xpEarned;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "last_attempt_at")
	private Instant lastAttemptAt;

	protected ItemProgress() {
		// constructor para JPA
	}

	public ItemProgress(String username, String itemId, Track track) {
		this.id = new ItemProgressId(username, itemId);
		this.track = track;
		this.status = Status.NOT_STARTED;
	}

	/**
	 * Registra un intento.
	 *
	 * @param score    puntuacion obtenida (0..1)
	 * @param awardedXp XP que se otorgan en este intento
	 * @param completed true si el intento da el reto por superado
	 */
	public void registerAttempt(double score, int awardedXp, boolean completed) {
		this.attempts += 1;
		this.lastAttemptAt = Instant.now();
		this.bestScore = Math.max(this.bestScore, score);

		if (completed) {
			this.status = Status.COMPLETED;
			if (this.completedAt == null) {
				this.completedAt = Instant.now();
			}
			// El XP de un reto se cobra una sola vez: repetirlo no debe permitir
			// farmear experiencia infinita.
			this.xpEarned = Math.max(this.xpEarned, awardedXp);
		}
		else if (this.status != Status.COMPLETED) {
			// Un intento fallido posterior NO puede tirar atras un reto ya
			// superado: el progreso es acumulativo.
			this.status = Status.IN_PROGRESS;
		}
	}

	public String getUsername() {
		return id.username();
	}

	public String getItemId() {
		return id.itemId();
	}

	public ItemProgressId getId() {
		return id;
	}

	public Track getTrack() {
		return track;
	}

	public Status getStatus() {
		return status;
	}

	public int getAttempts() {
		return attempts;
	}

	public double getBestScore() {
		return bestScore;
	}

	public int getXpEarned() {
		return xpEarned;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public Instant getLastAttemptAt() {
		return lastAttemptAt;
	}
}
