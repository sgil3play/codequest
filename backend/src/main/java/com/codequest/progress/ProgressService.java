package com.codequest.progress;

import com.codequest.design.DesignCatalog;
import com.codequest.domain.Track;
import com.codequest.kata.KataCatalog;
import com.codequest.progress.dto.ItemProgressView;
import com.codequest.progress.dto.ProgressSummary;
import com.codequest.quiz.QuizCatalog;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de progreso: experience, niveles, rachas y progreso por elemento.
 */
@Service
@Transactional
public class ProgressService {

	public static final String DEFAULT_USER = "sergio";

	private final ProgressRepository progressRepository;
	private final ItemProgressRepository itemProgressRepository;
	private final KataCatalog kataCatalog;
	private final QuizCatalog quizCatalog;
	private final DesignCatalog designCatalog;

	public ProgressService(ProgressRepository progressRepository, ItemProgressRepository itemProgressRepository,
			KataCatalog kataCatalog, QuizCatalog quizCatalog, DesignCatalog designCatalog) {
		this.progressRepository = progressRepository;
		this.itemProgressRepository = itemProgressRepository;
		this.kataCatalog = kataCatalog;
		this.quizCatalog = quizCatalog;
		this.designCatalog = designCatalog;
	}

	@Transactional(readOnly = true)
	public UserProgress getUserProgress(String username) {
		return progressRepository.findById(username).orElseGet(() -> new UserProgress(username));
	}

	@Transactional(readOnly = true)
	public List<ItemProgress> getItemProgress(String username) {
		return itemProgressRepository.findByIdUsername(username);
	}

	/**
	 * Progreso del usuario indexado por elemento.
	 *
	 * <p>Se resuelve en una sola consulta para evitar el patron N+1: sin esto,
	 * listar los retos haria una consulta por reto, y con cien retos, cien
	 * viajes de ida y vuelta a la base de datos.
	 *
	 * @param username perfil
	 * @return progreso indexado por identificador de elemento
	 */
	@Transactional(readOnly = true)
	public Map<String, ItemProgress> progressByItem(String username) {
		return itemProgressRepository.findByIdUsername(username).stream()
				.collect(Collectors.toMap(ItemProgress::getItemId, Function.identity()));
	}

	/** Vista de lectura del progreso, sin exponer la entidad JPA. */
	@Transactional(readOnly = true)
	public List<ItemProgressView> getItemProgressView(String username) {
		return getItemProgress(username).stream()
				.map(ItemProgressView::from)
				.toList();
	}

	/**
	 * Registra el resultado de un reto.
	 *
	 * @param username  perfil
	 * @param itemId    identificador del elemento
	 * @param track     pista del elemento
	 * @param score     puntuacion (0..1)
	 * @param maxXp     XP maximo del elemento
	 * @param completed true si el elemento queda superado
	 * @return progreso actualizado del elemento
	 */
	public ItemProgress recordAttempt(String username, String itemId, Track track, double score, int maxXp,
			boolean completed) {
		UserProgress user = progressRepository.findById(username).orElseGet(() -> new UserProgress(username));

		ItemProgress item = itemProgressRepository.findByIdUsernameAndIdItemId(username, itemId)
				.orElseGet(() -> new ItemProgress(username, itemId, track));

		boolean wasCompleted = item.getStatus() == ItemProgress.Status.COMPLETED;
		int awardedXp = completed ? maxXp : 0;

		item.registerAttempt(score, awardedXp, completed);
		itemProgressRepository.save(item);

		// El XP solo se entrega la primera vez que se completa el elemento.
		if (completed && !wasCompleted) {
			user.addXp(awardedXp);
		}
		user.registerActivity(LocalDate.now());
		progressRepository.save(user);

		return item;
	}

	@Transactional(readOnly = true)
	public ProgressSummary getSummary(String username) {
		UserProgress user = getUserProgress(username);
		List<ItemProgress> items = getItemProgress(username);

		int totalItems = kataCatalog.findAll().size() + quizCatalog.findAll().size() + designCatalog.findAll().size();
		int completed = (int) items.stream().filter(i -> i.getStatus() == ItemProgress.Status.COMPLETED).count();

		Map<Track, int[]> byTrack = new EnumMap<>(Track.class);
		for (Track track : Track.values()) {
			byTrack.put(track, new int[] {0, 0});
		}
		for (ItemProgress item : items) {
			int[] counters = byTrack.computeIfAbsent(item.getTrack(), t -> new int[] {0, 0});
			if (item.getStatus() == ItemProgress.Status.COMPLETED) {
				counters[0]++;
			}
		}
		for (var kata : kataCatalog.findAll()) {
			byTrack.get(kata.track())[1]++;
		}
		for (var quiz : quizCatalog.findAll()) {
			byTrack.get(quiz.track())[1]++;
		}
		for (var design : designCatalog.findAll()) {
			byTrack.get(design.track())[1]++;
		}

		List<ProgressSummary.TrackProgress> trackProgress = new ArrayList<>();
		for (Map.Entry<Track, int[]> entry : byTrack.entrySet()) {
			trackProgress.add(new ProgressSummary.TrackProgress(entry.getKey().name(), entry.getValue()[0],
					entry.getValue()[1]));
		}

		int level = user.getLevel();
		int xpToNext = LevelCalculator.xpForLevel(level + 1) - user.getTotalXp();

		return new ProgressSummary(
				username,
				user.getTotalXp(),
				level,
				LevelCalculator.progressWithinLevel(user.getTotalXp()),
				Math.max(xpToNext, 0),
				user.getStreakDays(),
				completed,
				totalItems,
				trackProgress,
				dailyQuestId(username),
				user.getLastActiveDate());
	}

	/**
	 * El reto recomendado del dia. Se elige de forma determinista a partir de la
	 * fecha, asi que durante todo el dia es el mismo, pero cambia cada dia.
	 */
	@Transactional(readOnly = true)
	public String dailyQuestId(String username) {
		List<ItemProgress> done = getItemProgress(username);
		List<String> completedIds = done.stream()
				.filter(i -> i.getStatus() == ItemProgress.Status.COMPLETED)
				.map(ItemProgress::getItemId)
				.toList();

		List<com.codequest.kata.KataDefinition> pending = kataCatalog.findAll().stream()
				.filter(k -> !completedIds.contains(k.id()))
				.sorted(Comparator.comparingInt(com.codequest.kata.KataDefinition::xp)
						.thenComparing(com.codequest.kata.KataDefinition::id))
				.toList();

		if (pending.isEmpty()) {
			return null;
		}
		int index = Math.floorMod(LocalDate.now().toEpochDay(), pending.size());
		return pending.get(index).id();
	}

	@Transactional(readOnly = true)
	public Optional<ItemProgress> getItemProgress(String username, String itemId) {
		return itemProgressRepository.findByIdUsernameAndIdItemId(username, itemId);
	}
}

