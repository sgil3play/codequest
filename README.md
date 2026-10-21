# CodeQuest

Plataforma de practica de codigo para preparar entrevistas tecnicas de backend
en Java. Los retos se ejecutan de verdad: el codigo se compila y se prueba en
una JVM aislada, con limite de tiempo, y el resultado vuelve al navegador caso
a caso.

El proyecto nace de una necesidad muy concreta: llevar meses fuera del codigo
de produccion y querer recuperar el ritmo escribiendo Java todos los dias. Por
eso el catalogo esta organizado en **pistas** que cubren bloques concretos de
una oferta tecnica (Spring Cloud, SOAP, TDD, Docker...), y no en retos
sueltos: se puede ver de un vistazo que huecos quedan.

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.3-6db33f)
![Java](https://img.shields.io/badge/Java-21-ed8b00)
![Angular](https://img.shields.io/badge/Angular-22-dd0031)
![JUnit](https://img.shields.io/badge/JUnit-5.11.4-25a162)
![H2](https://img.shields.io/badge/H2-embedded-0f4c81)

---

## Que hay dentro

| Pista | Que se practica |
| --- | --- |
| `JAVA_CORE` | Java 17+: records, `sealed`, switch expressions, `Optional`, streams, text blocks, enums con comportamiento |
| `JAVA_OOP` | SOLID, patron *Strategy*, arquitectura hexagonal |
| `SQL` | Consultas, indices, funciones de ventana, SQL contra Oracle |
| `SPRING_CORE` | Inyeccion de dependencias, transacciones, Spring Security |
| `SPRING_BOOT` | Autoconfiguracion, starters, perfiles, actuator, slices de test |
| `JPA` | Mappings, ciclo de vida, caches, locking, consultas N+1 |
| `SPRING_CLOUD` | Eureka, Gateway, OpenFeign, Config Server, Resilience4j, microservicios |
| `SOAP` | APIs SOAP, WSDL, integracion con sistemas legacy |
| `REST` | Verbos, codigos HTTP, versionado, HATEOAS, RFC7807 |
| `TDD` | Ciclo Red-Green-Refactor, mocks, tests parametrizados |
| `TESTING` | Tipos de test, piramide de testing, FIRST, mutation testing, contract tests |
| `DEVOPS` | Docker, CI/CD, Linux, cloud |
| `DESIGN` | Diseno de sistemas: escalabilidad, consistencia, trade-offs |

### Catalogo actual

**8 katas** (Java ejecutable con tests ocultos):

| Reto | Pista |
| --- | --- |
| `java-records-001` Record inmutable con validacion | `JAVA_CORE` |
| `java-sealed-001` Interfaces selladas y pattern matching exhaustivo | `JAVA_CORE` |
| `java-streams-001` Stream: agrupacion, top-N y resumen | `JAVA_CORE` |
| `java-optional-001` Optional bien usado: map, flatMap y fallback | `JAVA_CORE` |
| `java-textblocks-001` Text blocks y formateo de cadenas | `JAVA_CORE` |
| `java-enums-001` Enums con comportamiento y transiciones de estado | `JAVA_CORE` |
| `oop-strategy-001` Patron Strategy: cambiar el algoritmo en tiempo de ejecucion | `JAVA_OOP` |
| `oop-hexagonal-001` Puerto y adaptador: el dominio no depende de la tecnologia | `JAVA_OOP` |

**78 preguntas conceptuales** agrupadas por tema: Spring Core (2), Spring Boot
(10), JPA/Hibernate (12), Spring Security (1), Spring Cloud (3), SOAP y legacy
(2), SQL y Oracle (10), REST (10), TDD (2), Testing (10), DevOps (2),
Microservicios (2), Java moderno (2) y Diseno de sistemas (10).

**6 ejercicios de diseno de sistemas**: servicio de notificaciones multicanal,
API de consulta de movimientos bancarios, extraccion de datos de un core
bancario a una API de consulta, servicio de precios con reglas de pricing
dinamicas, pipeline de datos de ventas con agregados diarios y modulo de
autenticacion y autorizacion en microservicios.

Cada pista tiene tres formatos de practica:

- **Katas**: un enunciado, un editor y tests ocultos. El codigo se compila y se
  ejecuta de verdad.
- **Preguntas conceptuales**: opcion multiple con justificacion, que es
  exactamente lo que se pide en una entrevista.
- **Ejercicios de diseno**: un escenario con requisitos y preguntas que hay que
  responder, con una respuesta de referencia para autoevaluarse.

Ademas hay un sistema de **XP, niveles y rachas** que convierte el catalogo en
algo de lo que se vuelve al dia siguiente.

## Pila tecnica

- **Backend**: Java 21, Spring Boot 3.5.3, Spring Web, Spring Data JPA,
  Bean Validation, Actuator, springdoc (OpenAPI/Swagger).
- **Frontend**: Angular 22 (standalone, signals, rutas perezosas, `HttpClient`
  con `fetch`), SCSS, `marked` + `DOMPurify` para el Markdown.
- **Persistencia**: H2 embebido **en fichero** (`backend/data/codequest.mv.db`),
  asi que el progreso sobrevive a los reinicios del backend y no hace falta
  instalar nada. La URL sale de `CODEQUEST_DB_URL`, de modo que migrar a
  Postgres u Oracle es cambiar esa variable y el driver.
- **Ejecucion de codigo**: `javac` + `java` en una **JVM hija** con timeout,
  JUnit Platform Console Standalone 1.11.4.
- **Calidad**: 26 tests automatizados, JUnit 5 + Mockito, build en GitHub
  Actions.

## Arranque rapido

### Requisitos

- JDK 21
- Node.js 20 o superior (probado con Node 24)
- Maven 3.9 o superior (o usar el wrapper `mvnw`, que ya viene)

### Backend

```bash
cd backend
./mvnw spring-boot:run
```

La API queda en `http://localhost:8080`, la documentacion interactiva en
`http://localhost:8080/swagger-ui.html` y la salud en
`http://localhost:8080/actuator/health`.

### Frontend

```bash
cd frontend
npm install
npm start
```

La aplicacion queda en `http://localhost:4200`. En desarrollo el proxy de
`proxy.conf.json` redirige `/api` al backend en el puerto 8080, asi que no hay
CORS ni configuracion por entorno.

### Con Docker

```bash
docker compose up --build
```

Levanta el backend (8080) y el frontend (4200) ya proxyificado.

## Uso

1. En **Retos** se elige una pista y un reto.
2. Se escribe la solucion en el editor y se pulsa **Ejecutar tests**.
3. La respuesta trae el veredicto, el detalle de cada caso, la salida del
   compilador y lo que el codigo imprima por consola.
4. Si el codigo no compila, la salida del `javac` se muestra tal cual.
5. Al superar el reto se suman XP, sube el nivel y se actualiza la racha.

Las preguntas conceptuales se responden con un clic y cada respuesta viene
justificada, acierte o falle.

## El motor de ejecucion

Es la pieza mas interesante del proyecto y la que mas se ha trabajado.

```
POST /api/katas/{id}/submit
        |
        v
   KataRunner
        |  1. escribe el codigo en un directorio temporal
        |  2. javac  -> si falla, COMPILATION_ERROR con la salida del compilador
        |  3. java + JUnit console standalone -> TIMEOUT si se cuelga
        v
   Runner.java (generado)  ->  stdout: protocolo PASS| FAIL| ABORTED| SKIPPED|
                                    SUMMARY| ERROR|
        |
        v
   SubmissionResult (JSON)
```

Decisiones que importan:

- **El codigo del usuario nunca se ejecuta dentro del proceso de Spring.** Se
  compila y se ejecuta en una JVM hija, de modo que un bucle infinito, un
  `OutOfMemoryError` o un `System.exit` no tumba la aplicacion.
- **Doble limite de tiempo**: JUnit interrumpe el test que se cuelga
  (`-Djunit.jupiter.execution.timeout.default`) y, por debajo, el proceso hijo
  tiene un timeout global como red de seguridad.
- **Protocolo propio de salida.** `Runner.java` es un launcher generado en
  tiempo de ejecucion que imprime lineas `PASS|<caso>` y
  `SUMMARY|<pass>|<fail>|<abort>|<skip>`. El backend parsea ese protocolo y no
  el formato de JUnit: asi el formato de la respuesta es estable aunque cambie
  la version de JUnit.
- **`stdout` y `stderr` separados.** Lo que imprime el codigo del usuario va a
  `stderr`, de modo que no se mezcla con el protocolo ni se confunde con un
  fallo.
- **Aislamiento por proceso y tiempo, no por seguridad.** El codigo se ejecuta
  con los permisos del usuario que lanza la aplicacion. Es aceptable para uso
  local, pero no es un entorno de ejecucion de codigo arbitrario: en un
  entorno multiusuario habria que anadir contenedores o una sandbox de
  sistema operativo.

## Estructura del proyecto

```
codequest/
├── backend/                     Spring Boot 3.5.3 / Java 21
│   ├── src/main/java/com/codequest/
│   │   ├── kata/                catalogo, motor de ejecucion, controlador
│   │   ├── quiz/                preguntas conceptuales
│   │   ├── design/              ejercicios de diseno de sistemas
│   │   ├── progress/            XP, niveles, rachas, progreso por elemento
│   │   └── common/              configuracion, errores, lector de catalogos
│   ├── src/main/resources/
│   │   ├── data/                catalogos en YAML (katas, quizzes, design)
│   │   └── runner/Runner.java   launcher generado del motor
│   └── src/test/                26 tests
├── frontend/                    Angular 22
│   ├── src/app/core/            cliente HTTP, modelos, pipe de Markdown
│   └── src/app/pages/           dashboard, katas, quizzes, design
├── docs/ARCHITECTURE.md         decisiones tecnicas y alternativas descartadas
└── docker-compose.yml
```

## Catalogo en YAML

El contenido vive en ficheros YAML versionables (`backend/src/main/resources/data`)
y no en la base de datos. La razon es que el codigo Java de los tests va
incrustado, y en YAML se lee con literales de bloque, sin escapes:

```yaml
- id: java-optional-001
  track: JAVA_CORE
  title: "Optional bien usado: map, flatMap y fallback"
  starterCode: |
    public class Solution {
        public String emailSeguro(Empleado e) {
            throw new UnsupportedOperationException("TODO");
        }
    }
```

Ventajas: el contenido se revisa en un pull request, se versiona con el codigo
y no necesita migraciones.

Un detalle importante: **el YAML se lee con un mapper propio**
(`common/config/CatalogReader.java`), no con un bean `ObjectMapper`. Spring Boot
desactiva su configuracion automatica de Jackson en cuanto existe cualquier
bean de ese tipo, y la API pasaria a serializar en YAML en lugar de JSON.

## Tests

```bash
cd backend && ./mvnw test
```

26 tests en verde. Los que mas aportan:

| Test | Que protege |
| --- | --- |
| `KataRunnerTest` | El motor: compilacion, ejecucion, timeout, protocolo, codigo malicioso |
| `KataCatalogContentTest` | **Compila las 8 katas del catalogo**: un reto roto rompe la build |
| `CatalogContentTest` | Coherencia de los 3 catalogos: ids unicos, opciones distintas, indices en rango |
| `ProgressSummaryTest` | Que los totales del panel cuadren con el catalogo |
| `ItemProgressTest` | Que el progreso sea acumulativo y el XP se cobre una sola vez |

El test que mas valor tiene en la practica es `KataCatalogContentTest`: el
catalogo incluye codigo Java real, y sin ese test un reto con un test roto se
descubriria al usarlo, no al construirlo.

## Documentacion

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) - decisiones tecnicas, el motor
  de ejecucion en detalle, y las alternativas que se descartaron y por que.
- [Swagger UI](http://localhost:8080/swagger-ui.html) - la API completa.

## Licencia

MIT.
