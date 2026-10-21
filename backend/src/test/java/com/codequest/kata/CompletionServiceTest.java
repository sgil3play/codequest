package com.codequest.kata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codequest.common.config.RunnerProperties;
import com.codequest.kata.dto.CompletionItem;
import com.codequest.kata.dto.CompletionResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del autocompletado del editor.
 *
 * <p>Se prueba contra el compilador de verdad del JDK: el servicio no simula
 * nada, resolve los tipos con el mismo mecanismo que usa en produccion.
 */
class CompletionServiceTest {

	private CompletionService service;

	@BeforeEach
	void setUp() {
		service = new CompletionService(new RunnerProperties(5, 30, 30, 4, 262_144, null, null, null, null));
	}

	@Test
	@DisplayName("Propone los miembros de un String al escribir un punto")
	void proponeLosMiembrosDelTipoDelReceptor() {
		String code = """
				public class Solution {
				    public String upper(String s) {
				        return s.;
				    }
				}
				""";
		int offset = code.indexOf("s.") + 2;

		CompletionResponse response = service.complete("Solution", code, offset);

		List<String> labels = labels(response);
		assertTrue(labels.contains("toUpperCase()"), "esperaba toUpperCase() entre " + labels);
		assertTrue(labels.contains("trim()"), "esperaba trim() entre " + labels);
		assertTrue(labels.contains("length()"), "esperaba length() entre " + labels);
		assertTrue(labels.contains("toString()"), "esperaba toString() entre " + labels);
	}

	@Test
	@DisplayName("El prefijo que se esta escribiendo filtra la lista")
	void filtraPorElPrefijoEnCurso() {
		String code = """
				public class Solution {
				    public String upper(String s) {
				        return s.toU;
				    }
				}
				""";
		int offset = code.indexOf("s.toU") + 5;

		CompletionResponse response = service.complete("Solution", code, offset);

		List<String> labels = labels(response);
		assertFalse(labels.isEmpty(), "esperaba algun candidato");
		assertTrue(labels.stream().allMatch(label -> label.startsWith("toU")),
				"esperaba solo candidatos que empiezan por toU, pero vinieron " + labels);
		assertTrue(labels.contains("toUpperCase()"), "esperaba toUpperCase() entre " + labels);
	}

	@Test
	@DisplayName("El prefijo en camel case encuentra el miembro, como en un IDE")
	void encuentraPorCamelCase() {
		String code = """
				public class Solution {
				    public String upper(String s) {
				        return s.ts;
				    }
				}
				""";
		int offset = code.indexOf("s.ts") + 4;

		CompletionResponse response = service.complete("Solution", code, offset);

		assertTrue(labels(response).contains("toString()"), "esperaba toString() entre " + labels(response));
	}

	@Test
	@DisplayName("Dentro de una cadena de texto no se propone nada")
	void noProponeNadaDentroDeUnaCadena() {
		String code = """
				public class Solution {
				    public String hola() {
				        return "hola.";
				    }
				}
				""";
		int offset = code.indexOf("hola.") + 5;

		CompletionResponse response = service.complete("Solution", code, offset);

		assertTrue(response.items().isEmpty(), "esperaba una lista vacia, pero vinieron " + labels(response));
	}

	@Test
	@DisplayName("Dentro de un comentario no se propone nada")
	void noProponeNadaDentroDeUnComentario() {
		String code = """
				public class Solution {
				    // ver el String.
				    public String hola() {
				        return "hola";
				    }
				}
				""";
		int offset = code.indexOf("String.") + 7;

		CompletionResponse response = service.complete("Solution", code, offset);

		assertTrue(response.items().isEmpty(), "esperaba una lista vacia, pero vinieron " + labels(response));
	}

	@Test
	@DisplayName("Un tipo como receptor solo ofrece miembros estaticos")
	void enUnTipoSoloOfreceMiembrosEstaticos() {
		String code = """
				public class Solution {
				    public int parse(String s) {
				        return Integer.;
				    }
				}
				""";
		int offset = code.indexOf("Integer.") + 8;

		CompletionResponse response = service.complete("Solution", code, offset);

		List<String> labels = labels(response);
		assertTrue(labels.contains("parseInt(String)"), "esperaba parseInt(String) entre " + labels);
		assertFalse(labels.contains("toString()"), "no esperaba toString() entre " + labels);
	}

	@Test
	@DisplayName("Una instancia no ofrece miembros estaticos")
	void enUnaInstanciaNoOfreceMiembrosEstaticos() {
		String code = """
				public class Solution {
				    public int parse(Integer numero) {
				        return numero.;
				    }
				}
				""";
		int offset = code.indexOf("numero.") + 7;

		CompletionResponse response = service.complete("Solution", code, offset);

		List<String> labels = labels(response);
		assertTrue(labels.contains("intValue()"), "esperaba intValue() entre " + labels);
		assertFalse(labels.contains("parseInt(String)"), "no esperaba parseInt(String) entre " + labels);
	}

	@Test
	@DisplayName("Se ofrecen tambien los metodos privados de la propia clase")
	void ofreceLosMiembrosPrivadosDeLaPropiaClase() {
		String code = """
				public class Solution {
				    private int total() {
				        return 0;
				    }
				    public int usa() {
				        return this.;
				    }
				}
				""";
		int offset = code.indexOf("this.") + 5;

		CompletionResponse response = service.complete("Solution", code, offset);

		assertTrue(labels(response).contains("total()"), "esperaba total() entre " + labels(response));
	}

	@Test
	@DisplayName("Se ofrecen los metodos de una clase propia declarada en el mismo fichero")
	void ofreceLosMiembrosDeUnRecordLocal() {
		String code = """
				public class Solution {
				    record Empleado(String nombre, String email) {}

				    public String correo(Empleado e) {
				        return e.;
				    }
				}
				""";
		int offset = code.indexOf("return e.") + 9;

		CompletionResponse response = service.complete("Solution", code, offset);

		List<String> labels = labels(response);
		assertTrue(labels.contains("email()"), "esperaba email() entre " + labels);
		assertTrue(labels.contains("nombre()"), "esperaba nombre() entre " + labels);
	}

	@Test
	@DisplayName("El rango a sustituir es el identificador que se esta escribiendo")
	void devuelveElRangoASustituir() {
		String code = """
				public class Solution {
				    public String upper(String s) {
				        return s.toU;
				    }
				}
				""";
		int offset = code.indexOf("s.toU") + 5;

		CompletionResponse response = service.complete("Solution", code, offset);

		assertEquals(offset - 3, response.replaceStart(), "el rango empieza justo despues del punto");
		assertEquals(offset, response.replaceEnd(), "el rango acaba en el cursor");
	}

	@Test
	@DisplayName("Un cursor en mitad de un identificador sustituye el identificador entero")
	void sustituyeElIdentificadorCompleto() {
		String code = """
				public class Solution {
				    public String upper(String s) {
				        return s.toString();
				    }
				}
				""";
		int offset = code.indexOf("s.toStr") + 7;

		CompletionResponse response = service.complete("Solution", code, offset);

		assertEquals(offset - 5, response.replaceStart());
		assertEquals(code.indexOf("()"), response.replaceEnd(), "el rango acaba al final de toString");
	}

	@Test
	@DisplayName("Un array ofrece su campo length")
	void ofreceElLengthDeLosArrays() {
		String code = """
				public class Solution {
				    public int primero(String[] datos) {
				        return datos.;
				    }
				}
				""";
		int offset = code.indexOf("datos.") + 6;

		CompletionResponse response = service.complete("Solution", code, offset);

		assertTrue(labels(response).contains("length"), "esperaba length entre " + labels(response));
	}

	@Test
	@DisplayName("Un cursor que no va detras de un punto no propone nada")
	void sinPuntoNoProponeNada() {
		String code = """
				public class Solution {
				    public String upper(String s) {
				        return s;
				    }
				}
				""";
		int offset = code.indexOf("return s") + 8;

		CompletionResponse response = service.complete("Solution", code, offset);

		assertTrue(response.items().isEmpty(), "esperaba una lista vacia, pero vinieron " + labels(response));
	}

	private List<String> labels(CompletionResponse response) {
		return response.items().stream().map(CompletionItem::label).toList();
	}
}
