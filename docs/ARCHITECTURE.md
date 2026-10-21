# Arquitectura

Este documento explica **como esta construido CodeQuest y por que**. La idea
no es documentar las clases (eso lo hace el codigo), sino dejar por escrito las
decisiones que no son obvias y las alternativas que se descartaron.

---

## 1. Vision general

```
┌──────────────────────────────┐        ┌───────────────────────────────────┐
│  Frontend (Angular 22)       │        │  Backend (Spring Boot 3.5.3)      │
│                              │        │                                   │
│  dashboard / katas / quizzes │  HTTP  │  KataController  QuizController   │
│  design                      │ ─────► │  DesignController ProgressController│
│                              │  JSON  │                                   │
│  core/api.service.ts         │◄────── │  KataService     QuizService      │
│  core/models.ts              │        │  DesignService   ProgressService  │
└──────────────────────────────┘        └───────────┬───────────────────────┘
                                                       │
                            ┌──────────────────────────┼──────────────────────────┐
                            │                          │                          │
                   ┌────────▼────────┐        ┌────────▼────────┐      ┌────────▼────────┐
                   │  Catalogos YAML │        │  KataRunner     │      │  Progreso JPA   │
                   │  (inmutables)   │        │  (JVM hija)     │      │  (H2 / Postgres)│
                   └─────────────────┘        └────────┬────────┘      └─────────────────┘
                                                     │
                                          ┌──────────▼──────────┐
                                          │ javac + java -cp    │
                                          │ JUnit standalone    │
                                          │ Runner.java         │
                                          └─────────────────────┘
```

La idea central: **el nucleo del proyecto no es la API, es el motor de
ejecucion**. Lo demas (catalogo, progreso, frontend) existe para darle una
interfaz util a esa pieza.

---

## 2. El motor de ejecucion

### 2.1 Por que un proceso hijo

La primera version ejecutaba el codigo del usuario con `eval` o con la
biblioteca de compilacion en memoria. Se descarto por tres razones:

1. **Un bucle infinito tumba el servidor.** Sin un limite real, el hilo del
   servidor se queda colgado y no se puede recuperar.
2. **Un `System.exit(0)` mata la aplicacion.** Con codigo arbitrario esto no
   es una hipotesis: es lo primero que alguien prueba.
3. **Un `OutOfMemoryError` afecta al proceso entero**, no solo a la peticion.

La solucion es ejecutar en una **JVM hija**:

```
KataRunner.run()
  ├─ Files.createTempDirectory(...)            workspace aislado
  ├─ escribe Solution.java, SolutionTest.java, Runner.java
  ├─ ProcessRunner.run(javac ...)               timeout de compilacion
  │    └─ exit != 0  -> COMPILATION_ERROR con la salida del compilador
  ├─ ProcessRunner.run(java -cp ... Runner)     timeout de ejecucion
  │    └─ timeout    -> TIMEOUT
  └─ interpret(stdout)                          parseo del protocolo
```

### 2.2 Doble limite de tiempo

Hay dos limites, y ambos son necesarios:

| Limite | Valor | Quien lo aplica | Para que |
| --- | --- | --- | --- |
| Por caso | `timeLimitSeconds` del reto | JUnit (`junit.jupiter.execution.timeout.default`) | Cortar el test concreto que se cuelga y seguir con los demas |
| Global | `max(30, limite * 4)` | El proceso padre mata a la JVM hija | Red de seguridad: si JUnit no puede interrumpir (bucle que ignora la interrupcion, `Thread.stop` bloqueado, codigo nativo) |

El limite por caso es el que de verdad da valor: cuando un test se cuelga, el
resto de la suite sigue ejecutandose y la persona ve exactamente que caso se
ha quedado colgado. El limite global es la red que evita que el proceso se
quede colgado para siempre.

### 2.3 Protocolo propio

El launcher (`backend/src/main/resources/runner/Runner.java`) se apoya en la
JUnit Platform Launcher API pero **no imprime el formato de consola de JUnit**.
Emite un protocolo de lineas:

```
PASS|<nombre del caso>
FAIL|<nombre del caso>|<motivo>
ABORTED|<nombre del caso>|<motivo>
SKIPPED|<nombre del caso>|<motivo>
SUMMARY|<pass>|<fail>|<abort>|<skip>
ERROR|<mensaje>
```

Por que no parsear el formato de JUnit:

- El formato de consola de JUnit **cambia entre versiones** y esta pensado para
  consumo humano. Parsearlo es fragil.
- No depende del idioma ni de la localizacion.
- El escapado es trivial: `|` y `\` se escapan, y los saltos de linea se
  aplanan. El parser (`splitEscaped`/`unescape` en `KataRunner`) tiene 30
  lineas y no tiene casos raros.

### 2.4 stdout y stderr separados

El runner genera un `PrintStream` propio sobre `FileDescriptor.out` para el
protocolo y **desvia `System.out` a `stderr`** antes de ejecutar los tests:

```java
PrintStream protocol = new PrintStream(new FileOutputStream(FileDescriptor.out), true, UTF_8);
System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.err), true, UTF_8));
```

Si no se hiciera esto, un `System.out.println("PASS|algo")` en el codigo de la
persona falsearia el resultado. Es un detalle pequeno que evita un fallo
realmente confuso.

### 2.5 Localizar el jar de JUnit

Tres rutas, en este orden:

1. `codequest.runner.junit-console-jar` de la configuracion.
2. `target/junit-platform-console-standalone.jar`, que copia
   `maven-dependency-plugin` en la fase `generate-resources`. **Es la via
   determinista**: no depende del orden del classpath.
3. El code source de `org.junit.platform.console.ConsoleLauncher`.

El paso 3 tiene un detalle importante: se busca `ConsoleLauncher` y **no**
`LauncherFactory`. `LauncherFactory` tambien la aporta
`spring-boot-starter-test`, asi que su code source puede ser el jar de
Spring Boot y no el uber-jar de JUnit. En ese caso el codigo del usuario se
quedaria sin las clases de Jupiter en el classpath y todos los tests fallarian
con un `NoClassDefFoundError` dificil de diagnosticar.

### 2.6 Lo que este motor NO es

**No es un entorno seguro.** El aislamiento es de proceso y de tiempo, no de
seguridad: el codigo se ejecuta con los permisos del usuario que arranca la
aplicacion, puede leer ficheros, abrir sockets y lanzar procesos.

Para uso local (una persona practicando en su portatil) es aceptable. Para
exponerlo a terceros haria falta:

- Contenedor sin privilegios (`--read-only`, `--network=none`, `--cap-drop=ALL`)
  o Firecracker/gVisor.
- Perfil de Java con limites del sistema (`-XX:MaxRAMPercentage`, cgroups).
- Lista blanca de clases y metodos (`SecurityManager`, hoy obsoleto, o
  instrumentacion de bytecode).

Esta limitacion esta documentada en el propio codigo (`KataRunner`), no solo
aqui, porque es el punto que un revisor debe ver primero.

---

## 3. Catalogo en YAML

### 3.1 Por que YAML y no JSON

El catalogo incluye codigo Java real (el enunciado, el codigo inicial, la
suite de tests). En JSON habia que escribir `\n` a mano en cada linea y
escapar las comillas de los `assertEquals`. En YAML se usa un literal de
bloque:

```yaml
  starterCode: |
    public class Solution {
        public String emailSeguro(Empleado e) {
            throw new UnsupportedOperationException("TODO");
        }
    }
```

Ademas, el catalogo **se versiona con el codigo**: anadir un reto es un
`git add` y un commit, sin migraciones de base de datos ni_ENDPOINT de
administracion.

### 3.2 El lector de catalogos

El detalle importante de esta parte no es el formato, sino **como se lee**.

La primera implementacion registraba el mapper de YAML como un bean:

```java
@Bean
public ObjectMapper yamlObjectMapper() { ... }   // ESTO ESTABA MAL
```

Funcionaba... hasta que la API empezo a devolver **YAML en lugar de JSON**.
La causa: `JacksonAutoConfiguration` de Spring Boot esta anotada con
`@ConditionalOnMissingBean(ObjectMapper.class)`. En cuanto existe **cualquier**
bean de ese tipo -aunque tenga un `@Qualifier` distinto y nadie lo use para
serializar respuestas- la configuracion automatica de Jackson se desactiva y
no se crea el mapper JSON.

La solucion fue Sacar el mapper de YAML del contenedor de Spring y meterlo
dentro de una clase propia:

```java
@Component
public class CatalogReader {
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    public <T> List<T> readList(String resource, Class<T> type) { ... }
}
```

`CatalogReader` no es un `ObjectMapper`, asi que Spring Boot sigue
configurando el suyo y la API serializa en JSON. Es un poco menos idiomatico,
pero el tipo del bean deja de ser ambiguo.

### 3.3 Reglas del YAML que seabloquean en silencio

El catalogo es el activo mas fragil del proyecto. Estas reglas, aprendidas
por la via dura:

- Un escalar plano **no puede empezar por un caracter reservado** (`@`, `` ` ``,
  `&`, `*`, `!`, `%`, `>`, `|`, `{`, `[`...). Con comillas o literal de bloque.
- Un escalar plano **no puede contener `": "`** (dos puntos + espacio) sin
  comillas, porque YAML lo interpretaria como un mapa.
- Los elementos de lista que contienen `:` se entrecomillan: `'...: ...'`.
- Las comillas simples multilinea tienen que cerrarse.
- La indentacion dentro de un literal de bloque tiene que ser **uniforme**: una
  linea con 3 espacios donde las demas tienen 4 rompe el bloque entero y el
  error aparece lejos de la causa.

Por eso existe `CatalogContentTest`: valida que los tres catalogos cargan, que
los ids son unicos, que las opciones de una pregunta son distintas y que el
indice correcto esta en rango. Un error de formato se ve ahi, no en produccion.

---

## 4. Progreso, XP y niveles

### 4.1 Modelo

Dos entidades:

- `UserProgress`: el agregado por persona (XP total, nivel, racha, ultima
  actividad). Clave primaria: el nombre de usuario.
- `ItemProgress`: el progreso sobre un elemento concreto (estado, intentos,
  mejor puntuacion, XP ganada). Clave primaria compuesta
  (`username`, `itemId`).

La clave compuesta se declara con `@EmbeddedId` sobre un **record**:

```java
@Embeddable
public record ItemProgressId(String username, String itemId) {}
```

y no con dos campos `@Id`. Motivo: Hibernate exige que las clases de clave
compuesta implementen `equals` y `hashCode` correctamente, y un record lo
hace por definicion. Ademas, `record` evita el constructor sin argumentos que
Hibernate reclama con las claves clases.

**Consecuencia en las consultas**: al anidarse los campos bajo `id`, los
nombres de los metodos de repositorio cambian:

```java
List<ItemProgress> findByIdUsername(String username);
Optional<ItemProgress> findByIdUsernameAndIdItemId(String username, String itemId);
```

Spring Data resuelve las rutas anidadas escribiendo el nombre del embeddable
delante del atributo. Con los dos `@Id` planos, los nombres eran
`findByUsername` y `findByUsernameAndItemId`.

### 4.2 Reglas de negocio

- **El progreso es acumulativo.** Un intento fallido posterior a un reto ya
  superado **no** lo devuelve a "en curso". El `bestScore` tampoco baja.
- **El XP se cobra una sola vez.** Repetir un reto da XP 0 adicional. Sin esta
  regla se podrian farmear XP indefinidamente.
- **La fecha de completado** se fija en el primer intento superado y no se
  vuelve a mover.

Las tres reglas tienen tests de regresion en `ItemProgressTest` y
`ProgressSummaryTest`, porque las tres son bugs que ya estaban en el codigo y
que no se ven mirando la aplicacion: se ven mirando los numeros del panel.

### 4.3 Niveles

`LevelCalculator` usa umbrales fijos y crecientes: 0, 100, 300, 600... La
curva es cuadratica a proposito: el nivel 2 llega en minutos (para que la
persona vea una recompensa rapido) y a partir del nivel 5 cuesta mas (para
que los retos nominally faciles valgan algo).

---

## 5. Frontend

### 5.1 Angular standalone y signals

Angular 22 con componentes standalone, `ChangeDetectionStrategy.OnPush` y
signals en todas partes:

```ts
protected readonly katas = toSignal(this.api.listKatas(), { initialValue: [] as KataSummary[] });
protected readonly filtered = computed(() => { ... });
```

Se eligio `toSignal` + `computed` en lugar de `httpResource` porque `toSignal`
es estable desde hace muchas versiones y su semantica es obvia: la peticion se
lanza una vez y el signal emite el ultimo valor. `httpResource` es mas
idiomatico en la v22, pero su API todavia se esta moviendo y para una app que
se va a revisar en una entrevista conviene la opcion que se lee de un vistazo.

### 5.2 Rutas perezosas

Cada pagina es un `loadComponent`. El bundle inicial son 273 kB (74 kB
comprimido) y cada pagina baja en su propio chunk:

```
main          268.56 kB
katas           6.44 kB
kata-detail     9.61 kB
quizzes         8.65 kB
design          2.48 kB
design-detail   6.12 kB
```

### 5.3 Proxy en desarrollo

`proxy.conf.json` redirige `/api` a `http://localhost:8080`. Asi el frontend
siempre habla con rutas relativas: no hay CORS, ni URLs por entorno, ni
diferencias entre desarrollo y produccion. En produccion el mismo origen
sirve los dos.

### 5.4 Markdown

El contenido de los catalogos esta escrito en Markdown. Se renderiza con
`marked` y se sanea con `DOMPurify` antes de inyectarlo:

```ts
const html = DOMPurify.sanitize(marked.parse(source, { async: false, gfm: true }) as string);
return this.sanitizer.bypassSecurityTrustHtml(html);
```

El `bypassSecurityTrustHtml` es seguro **porque** lo que se inyecta ya ha
pasado por DOMPurify. Sin ese paso, el `bypass` seria una puerta abierta.

Los resultados se cachean por texto en un `Map`: el contenido es estatico y se
re-renderiza en cada deteccion de cambios.

---

## 6. API

| Metodo | Ruta | Que hace |
| --- | --- | --- |
| `GET` | `/api/katas?track=&user=` | Lista de retos con el estado de progreso |
| `GET` | `/api/katas/{id}?user=` | Detalle de un reto (sin la solucion) |
| `POST` | `/api/katas/{id}/submit` | Compila, ejecuta y devuelve el veredicto |
| `GET` | `/api/katas/{id}/solution` | Revela la solucion comentada |
| `GET` | `/api/quizzes?track=` | Preguntas agrupadas por tema (sin la correcta) |
| `POST` | `/api/quizzes/{id}/answer?user=` | Responde y devuelve la justificacion |
| `GET` | `/api/design` | Lista de ejercicios de diseno |
| `GET` | `/api/design/{id}` | Detalle sin la respuesta de referencia |
| `GET` | `/api/design/{id}/reference` | Revela la respuesta de referencia |
| `GET` | `/api/progress/summary?user=` | XP, nivel, racha, progreso por pista |
| `GET` | `/actuator/health` | Salud de la aplicacion |

La respuesta correcta de una pregunta **nunca viaja al cliente antes de
tiempo**: el DTO `QuizQuestionView` no tiene el campo `correctIndex`. Lo mismo
con la solucion de un kata y la respuesta de referencia de un ejercicio de
diseno: van en endpoints aparte, que se piden de forma explicita.

---

## 7. Decisiones descartadas

| Alternativa | Por que se descarto |
| --- | --- |
| Base de datos para el catalogo | El catalogo es de solo lectura y cambia con un commit. Una tabla seria mas infraestructura y migraciones para ganar nada |
| Base de datos para el progreso | Un H2 en memoria hace que `git clone && mvnw spring-boot:run` funcione sin instalar nada. El progreso es de un solo usuario y efimero |
| Ejecucion del codigo en el proceso de Spring | Un bucle infinito o un `System.exit` tumba el servidor. Ver seccion 2.1 |
| Parsear la salida de JUnit | El formato cambia entre versiones y esta pensado para consumo humano. Ver seccion 2.3 |
| Bean `ObjectMapper` de YAML | Desactiva la configuracion de Jackson de Spring Boot y la API responde en YAML. Ver seccion 3.2 |
| WebSockets / SSE para el progreso | Las ejecuciones duran segundos. Un POST normal es mas simple y suficiente |
| Estado global en un store (Redux / signals raiz) | El estado de cada pagina es local y no se comparte. Una signal por pagina es suficiente |
| Autenticacion | Es una herramienta local de un solo usuario. Anadir Spring Security sin una necesidad real seria infraestructura de mas |

---

## 8. Limitaciones conocidas

1. **El runner no es un sandbox de seguridad.** Ver seccion 2.6.
2. **Un solo usuario.** El parametro `user` viaja en la query string y no hay
   autenticacion: es una herramienta de practica individual.
3. **El progreso se pierde al reiniciar** (H2 en memoria). Es deliberado, pero
   conviene saberlo.
4. **El catalogo es pequeno.** 8 katas, 20 preguntas y 6 ejercicios de diseno.
   Suficiente para demostrar el mecanismo, no para ser un producto.
5. **El limite de compilacion y el global son valores fijos** en el codigo
   (`max(30, limite * 4)`). Con una carga concurrente alta habria que pasar a
   un pool de procesos con cola.

## 9. Pasos siguientes

- Pool de procesos de ejecucion con cola y limites de concurrencia.
- Ejecutor de SQL contra H2 en memoria, con el esquema y los datos de prueba
  del enunciado.
- Soporte de `Mockito` para las katas de TDD (el classpath del proceso hijo
  necesita las librerias de test).
- Catalogo en base de datos con edicion desde la propia aplicacion.
- Autenticacion y varios perfiles, si la herramienta pasa a ser compartida.
