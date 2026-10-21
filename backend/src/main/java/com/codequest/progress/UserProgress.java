package com.codequest.progress;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Progreso agregado de una persona.
 *
 * <p>El modelo es de un unico usuario por defecto (sin autenticacion) para no
 * anadir ruido al MVP, pero todas las consultas van por {@code username}, de
 * modo que anadir login multi-usuario es un cambio de esquema, no de logica.
 */
@Entity
@Table(name = "user_progress")
public class UserProgress {

	@Id
	@Column(name = "username", nullable = false, length = 64)
	private String username;

	@Column(name = "total_xp", nullable = false)
	private int totalXp;

	@Column(name = "level", nullable = false)
	private int level;

	@Column(name = "streak_days", nullable = false)
	private int streakDays;

	@Column(name = "last_active_date")
	private LocalDate lastActiveDate;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected UserProgress() {
		// constructor para JPA
	}

	public UserProgress(String username) {
		this.username = username;
		this.totalXp = 0;
		this.level = 1;
		this.streakDays = 0;
		this.createdAt = Instant.now();
	}

	public void addXp(int xp) {
		this.totalXp += xp;
		this.level = LevelCalculator.levelFor(totalXp);
	}

	public String getUsername() {
		return username;
	}

	public int getTotalXp() {
		return totalXp;
	}

	public int getLevel() {
		return level;
	}

	public int getStreakDays() {
		return streakDays;
	}

	public LocalDate getLastActiveDate() {
		return lastActiveDate;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	/**
	 * Actualiza la racha de dias consecutivos con actividad.
	 * Si la ultima actividad fue ayer, la racha continua; si fue antes, se reinicia.
	 */
	public void registerActivity(LocalDate today) {
		if (lastActiveDate == null) {
			streakDays = 1;
		}
		else if (lastActiveDate.equals(today)) {
			// Ya se habia registrado actividad hoy: no se altera la racha.
			return;
		}
		else if (lastActiveDate.equals(today.minusDays(1))) {
			streakDays += 1;
		}
		else {
			streakDays = 1;
		}
		this.lastActiveDate = today;
	}
}
