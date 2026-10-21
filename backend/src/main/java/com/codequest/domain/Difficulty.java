package com.codequest.domain;

/**
 * Dificultad de un reto. El orden del enum define el peso en XP y el color en la UI.
 */
public enum Difficulty {
	EASY(1),
	MEDIUM(2),
	HARD(3),
	EXPERT(4);

	private final int weight;

	Difficulty(int weight) {
		this.weight = weight;
	}

	public int getWeight() {
		return weight;
	}
}
