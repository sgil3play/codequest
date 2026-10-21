package com.codequest.kata;

import com.codequest.common.config.JunitConsoleJar;
import com.codequest.common.config.RunnerProperties;
import com.codequest.kata.dto.CompletionItem;
import com.codequest.kata.dto.CompletionResponse;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Autocompletado del editor de retos, al estilo de un IDE.
 *
 * <p>La pregunta que responde el servicio es: "estoy escribiendo esto, {@code s.}
 * y {@code s} es un {@code String}: que miembros puedo escribir?". Para
 * contestarla hace falta el <b>tipo</b> de la expresion que precede al punto, y
 * el tipo solo lo sabe el compilador.
 *
 * <p>El servicio usa la API del compilador del propio JDK
 * ({@code com.sun.source}), sin dependencias externas:
 * <ol>
 *   <li>Se analiza el texto con un scanner lexico para saber si el cursor esta
 *       en codigo y donde esta el punto.</li>
 *   <li>Se sustituye el identificador que se esta escribiendo por una sonda
 *       ({@code __cqProbe__}). El punto queda entonces seguido de un
 *       identificador que no existe, lo que da al compilador un arbol sintactico
 *       completo sin tener que inventar codigo.</li>
 *   <li>Se compila y se analiza ese fuente con {@code JavacTask}. El compilador
 *       resuelve el tipo del receptor (que es lo unico que importa) y avisa de
 *       que la sonda no existe, que es un error esperado y sin consecuencias.</li>
 *   <li>Se piden los miembros del tipo con {@code Elements.getAllMembers}, que
 *       incluye los heredados, y se filtran por lo que es realmente accesible.</li>
 * </ol>
 *
 * <p><b>Seguridad.</b> El codigo de la persona no se ejecuta nunca: solo se
 * analiza. El compilador se invoca con {@code -proc:none}, de modo que no se
 * lanzan los procesadores de anotaciones que hubieran en el classpath, y el
 * analisis se queda en la fase de atribucion, sin generar ficheros de clase.
 *
 * <p><b>Limites.</b> El analisis se ejecuta en un hilo aparte con un tiempo
 * maximo y un numero de analisis simultaneos acotado, para que un fuente
 * patologico no pueda dejar el servidor sin recursos.
 */
@Service
public class CompletionService {

	private static final Logger log = LoggerFactory.getLogger(CompletionService.class);

	/**
	 * Nombre de la sonda que se inyecta en el punto. Es un identificador
	 * valido y suficientemente raro como para que no colisione con el codigo de
	 * la persona.
	 */
	private static final String PROBE = "__cqProbe__";

	/** Tope de candidatos devueltos, para no monsterizar la respuesta. */
	private static final int MAX_ITEMS = 150;

	/** Tiempo maximo de un analisis. */
	private static final long TIMEOUT_MILLIS = 2500;

	/** Analisis simultaneos. El compilador del JDK es pesado: dos ya es mucho. */
	private static final int MAX_PARALLEL = 2;

	/**
	 * Miembros de {@code java.lang.Object} que no aportan nada al completar y
	 * que en un editor llenarian la lista de ruido.
	 */
	private static final Set<String> IGNORED_MEMBERS = Set.of("wait", "notify", "notifyAll", "finalize");

	private final RunnerProperties props;
	private final Semaphore slots = new Semaphore(MAX_PARALLEL);
	private final ExecutorService executor;

	public CompletionService(RunnerProperties props) {
		this.props = props;
		this.executor = Executors.newCachedThreadPool(runnable -> {
			Thread thread = new Thread(runnable, "codequest-completion");
			thread.setDaemon(true);
			return thread;
		});
	}

	/**
	 * Devuelve los miembros que se pueden escribir en la posicion del cursor.
	 *
	 * @param className nombre del fichero que se esta editando
	 * @param code      codigo Java completo del editor
	 * @param offset    posicion del cursor
	 * @return candidatos y el rango de texto que hay que sustituir
	 */
	public CompletionResponse complete(String className, String code, int offset) {
		if (code == null || code.isBlank() || offset < 0 || offset > code.length()) {
			return empty();
		}

		JavaSourceScanner scanner = JavaSourceScanner.of(code);
		// Dentro de una cadena o de un comentario no hay miembros que completar.
		if (!scanner.isCode(offset)) {
			return empty();
		}

		int dot = scanner.dotBefore(offset);
		if (dot < 0) {
			return empty();
		}

		String prefix = code.substring(dot + 1, offset);
		if (!scanner.isIdentifierPrefix(prefix)) {
			return empty();
		}

		// Se sustituye el identificador en curso por la sonda. Si el cursor esta
		// en medio de un identificador, se sustituye el identificador entero: es
		// lo que haria un IDE al aceptar una sugerencia.
		int identifierEnd = scanner.identifierEnd(offset);
		String analysed = code.substring(0, dot + 1) + PROBE + code.substring(identifierEnd);

		List<CompletionItem> items = analyse(className, analysed, prefix);
		return new CompletionResponse(items, dot + 1, identifierEnd);
	}

	/* --------------------------- analisis --------------------------- */

	private List<CompletionItem> analyse(String className, String analysed, String prefix) {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			// Solo pasa si el backend corre sobre un JRE. El arranque no falla
			// por esto: el autocompletado es una ayuda, no una funcion critica.
			log.warn("No hay compilador de Java disponible: el autocompletado queda desactivado");
			return List.of();
		}
		if (!slots.tryAcquire()) {
			log.debug("Se rechaza un analisis de autocompletado: ya hay {} en curso", MAX_PARALLEL);
			return List.of();
		}

		Future<List<CompletionItem>> future = executor.submit(() -> compute(compiler, className, analysed, prefix));
		try {
			return future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException ex) {
			future.cancel(true);
			log.warn("El analisis de autocompletado ha superado el limite de {} ms", TIMEOUT_MILLIS);
			return List.of();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return List.of();
		}
		catch (ExecutionException ex) {
			log.debug("No se pudo analizar el codigo para el autocompletado", ex.getCause());
			return List.of();
		}
		finally {
			slots.release();
		}
	}

	private List<CompletionItem> compute(JavaCompiler compiler, String className, String analysed, String prefix)
			throws IOException {
		StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
		try {
			JavacTask task = (JavacTask) compiler.getTask(
					null,
					fileManager,
					// Los diagnosticos se descartan: el error de la sonda es
					// esperado y no interesa que ensucie la consola del servidor.
					diagnostic -> {
					},
					List.of(
							// Sin procesadores de anotaciones: el codigo de la persona
							// no debe poder ejecutar nada, ni siquiera al analizarlo.
							"-proc:none",
							"-nowarn",
							"-Xlint:none",
							"-classpath", classpath()),
					null,
					List.of(new StringSource(className + ".java", analysed)));

			Iterable<? extends CompilationUnitTree> units = task.parse();
			task.analyze();

			CompilationUnitTree unit = units.iterator().next();
			TreePath probe = findProbe(unit);
			if (probe == null) {
				return List.of();
			}

			MemberSelectTree select = (MemberSelectTree) probe.getLeaf();
			TreePath receiver = new TreePath(probe, select.getExpression());

			Trees trees = Trees.instance(task);
			TypeMirror type = trees.getTypeMirror(receiver);
			if (type == null) {
				return List.of();
			}

			boolean staticContext = isStaticReference(trees, unit, receiver, analysed);
			return candidates(task, unit, trees, type, staticContext, prefix);
		}
		catch (RuntimeException | Error ex) {
			// Un codigo a medio escribir puede hacer que el compilador explote con
			// errores internos. El autocompletado se limita a no responder.
			log.debug("El compilador no pudo analizar el codigo: {}", ex.toString());
			return List.of();
		}
		finally {
			try {
				fileManager.close();
			}
			catch (IOException | RuntimeException ex) {
				log.debug("No se pudo cerrar el gestor de ficheros del compilador", ex);
			}
		}
	}

	/** Busca el punto del arbol donde se ha inyectado la sonda. */
	private TreePath findProbe(CompilationUnitTree unit) {
		final TreePath[] found = new TreePath[1];
		new TreePathScanner<Void, Void>() {
			@Override
			public Void visitMemberSelect(MemberSelectTree node, Void unused) {
				if (found[0] == null && PROBE.contentEquals(node.getIdentifier())) {
					found[0] = getCurrentPath();
				}
				return super.visitMemberSelect(node, unused);
			}
		}.scan(unit, null);
		return found[0];
	}

	/**
	 * Indica si el receptor es un tipo (y no una instancia), es decir si lo que
	 * se escribe despues del punto tiene que ser estatico.
	 */
	private boolean isStaticReference(Trees trees, CompilationUnitTree unit, TreePath receiver, String source) {
		SourcePositions positions = trees.getSourcePositions();
		long start = positions.getStartPosition(unit, receiver.getLeaf());
		long end = positions.getEndPosition(unit, receiver.getLeaf());
		String text = (start >= 0 && end > start && end <= source.length())
				? source.substring((int) start, (int) end)
				: "";

		// "this" y "super" son instancias, aunque el compilador los modele como
		// si fueran el propio tipo.
		if (text.equals("this") || text.equals("super")) {
			return false;
		}

		Element element = trees.getElement(receiver);
		if (element instanceof TypeElement) {
			return true;
		}
		return element != null && element.getModifiers().contains(Modifier.STATIC);
	}

	/* --------------------------- candidatos --------------------------- */

	private List<CompletionItem> candidates(JavacTask task, CompilationUnitTree unit, Trees trees, TypeMirror type,
			boolean staticContext, String prefix) {
		// Los arrays no son tipos declarados: su unico miembro es "length".
		if (type.getKind() == TypeKind.ARRAY) {
			return List.of(new CompletionItem("length", "field", "int length", "length", false, false));
		}
		if (type.getKind() != TypeKind.DECLARED) {
			return List.of();
		}

		Element element = task.getTypes().asElement(type);
		if (!(element instanceof TypeElement typeElement)) {
			// Un paquete no tiene miembros que completar.
			return List.of();
		}

		Set<Element> localTypes = localTypes(unit, trees);

		List<Candidate> candidates = new ArrayList<>();
		for (Element member : task.getElements().getAllMembers(typeElement)) {
			CompletionItem item = toItem(member, localTypes, staticContext);
			if (item == null) {
				continue;
			}
			int score = score(nameOf(member), prefix);
			if (score < 0) {
				continue;
			}
			candidates.add(new Candidate(item, score));
		}

		candidates.sort(Comparator
				.comparingInt(Candidate::score)
				.thenComparingInt(candidate -> candidate.item().inherited() ? 1 : 0)
				.thenComparing(candidate -> candidate.item().label(), String.CASE_INSENSITIVE_ORDER));

		return candidates.stream().limit(MAX_ITEMS).map(Candidate::item).toList();
	}

	/**
	 * Tipos declarados en el fichero que se esta editando.
	 *
	 * <p>Sirven para saber que miembros son "propios": los declarados en el
	 * codigo que la persona esta escribiendo se pueden usar aunque sean
	 * privados, porque el codigo esta dentro de ellos. En cambio, los
	 * miembros privados de una clase de la biblioteca (los internos de
	 * {@code java.lang.String}, por ejemplo) no son accesibles y no se
	 * ofrecen.
	 */
	private Set<Element> localTypes(CompilationUnitTree unit, Trees trees) {
		Set<Element> types = new HashSet<>();
		for (Tree declaration : unit.getTypeDecls()) {
			TreePath path = trees.getPath(unit, declaration);
			if (path == null) {
				continue;
			}
			Element element = trees.getElement(path);
			if (element != null) {
				types.add(element);
			}
		}
		return types;
	}

	/** Convierte un elemento del modelo de Java en un candidato, o null si no procede. */
	private CompletionItem toItem(Element member, Set<Element> localTypes, boolean staticContext) {
		ElementKind kind = member.getKind();
		if (kind != ElementKind.METHOD
				&& kind != ElementKind.FIELD
				&& kind != ElementKind.ENUM_CONSTANT
				&& kind != ElementKind.CLASS
				&& kind != ElementKind.INTERFACE
				&& kind != ElementKind.ENUM
				&& kind != ElementKind.RECORD) {
			return null;
		}

		String name = member.getSimpleName().toString();
		if (IGNORED_MEMBERS.contains(name)) {
			return null;
		}

		boolean isStatic = member.getModifiers().contains(Modifier.STATIC);
		boolean own = localTypes.contains(member.getEnclosingElement());
		boolean inherited = !own;

		// En un contexto de instancia no se ofrecen los miembros estaticos y en
		// uno de tipo solo los estaticos: es lo que realmente se puede escribir.
		if (staticContext != isStatic) {
			return null;
		}

		// Solo se ofrecen los miembros visibles desde el editor: los publicos y
		// los declarados en el propio fichero (que se pueden usar aunque sean
		// privados, porque el codigo esta dentro de ellos).
		if (!own && !member.getModifiers().contains(Modifier.PUBLIC)) {
			return null;
		}

		return switch (kind) {
			case METHOD -> {
				ExecutableElement method = (ExecutableElement) member;
				List<String> parameters = method.getParameters().stream()
						.map(parameter -> simpleTypeName(parameter.asType()))
						.toList();
				String insertText = name + "(" + String.join(", ", parameters) + ")";
				String detail = simpleTypeName(method.getReturnType()) + " " + insertText;
				yield new CompletionItem(insertText, "method", detail, insertText, isStatic, inherited);
			}
			case ENUM_CONSTANT -> {
				String detail = simpleTypeName(member.asType()) + " " + name;
				yield new CompletionItem(name, "constant", detail, name, isStatic, inherited);
			}
			case FIELD -> {
				String detail = simpleTypeName(member.asType()) + " " + name;
				yield new CompletionItem(name, "field", detail, name, isStatic, inherited);
			}
			default -> {
				String detail = simpleTypeName(member.asType());
				yield new CompletionItem(name, "type", detail, name, isStatic, inherited);
			}
		};
	}

	private String nameOf(Element member) {
		return member.getSimpleName().toString();
	}

	/** Nombre simple de un tipo, sin paquetes ni parametros de tipo. */
	private String simpleTypeName(TypeMirror type) {
		if (type == null) {
			return "?";
		}
		if (type.getKind() == TypeKind.VOID) {
			return "void";
		}
		if (type.getKind() == TypeKind.ARRAY) {
			return simpleTypeName(((ArrayType) type).getComponentType()) + "[]";
		}
		if (type.getKind() == TypeKind.DECLARED) {
			Element element = ((DeclaredType) type).asElement();
			if (element instanceof TypeElement typeElement) {
				return typeElement.getSimpleName().toString();
			}
		}
		if (type.getKind() == TypeKind.TYPEVAR) {
			return type.toString();
		}
		return type.toString();
	}

	/* --------------------------- prefijo --------------------------- */

	/**
	 * Puntua como encaja un miembro con el prefijo que se esta escribiendo.
	 * Un numero negativo significa que no encaja.
	 */
	private int score(String name, String prefix) {
		if (prefix.isEmpty()) {
			return 3;
		}
		if (name.startsWith(prefix)) {
			return 0;
		}
		if (name.regionMatches(true, 0, prefix, 0, prefix.length())) {
			return 1;
		}
		if (camelMatches(name, prefix)) {
			return 2;
		}
		return -1;
	}

	/**
	 * Compara el prefijo con las "jorobas" de camel case del nombre, como
	 * hace un IDE: {@code ts} encuentra {@code toString}.
	 */
	private boolean camelMatches(String name, String prefix) {
		List<String> humps = humps(name);
		if (humps.isEmpty() || prefix.isEmpty()) {
			return false;
		}

		int position = 0;
		String first = humps.get(0);
		int taken = 0;
		while (taken < first.length() && position < prefix.length() && first.charAt(taken) == prefix.charAt(position)) {
			taken++;
			position++;
		}
		if (taken == 0) {
			return false;
		}

		for (int h = 1; h < humps.size() && position < prefix.length(); h++) {
			char expected = humps.get(h).charAt(0);
			char actual = prefix.charAt(position);
			if (Character.toLowerCase(expected) != Character.toLowerCase(actual)) {
				return false;
			}
			position++;
		}
		return position == prefix.length();
	}

	/** Divide un nombre en sus jorobas de camel case: {@code toString} en to, String. */
	private List<String> humps(String name) {
		List<String> humps = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (i > 0 && Character.isUpperCase(c) && !current.isEmpty()) {
				humps.add(current.toString());
				current.setLength(0);
			}
			current.append(c);
		}
		if (!current.isEmpty()) {
			humps.add(current.toString());
		}
		return humps;
	}

	/* --------------------------- utilidades --------------------------- */

	private String classpath() {
		return JunitConsoleJar.locate(props.junitConsoleJar());
	}

	private CompletionResponse empty() {
		return new CompletionResponse(List.of(), 0, 0);
	}

	/** Candidato con su puntuacion de encaje con el prefijo. */
	private record Candidate(CompletionItem item, int score) {
	}

	/**
	 * Fuente Java en memoria. El nombre del fichero importa: si el codigo
	 * declara una clase publica, el compilador exige que se llame asi.
	 */
	private static final class StringSource extends SimpleJavaFileObject {

		private final String content;

		private StringSource(String name, String content) {
			super(URI.create("string:///" + name), Kind.SOURCE);
			this.content = content;
		}

		@Override
		public CharSequence getCharContent(boolean ignoreEncodingErrors) {
			return content;
		}
	}
}
