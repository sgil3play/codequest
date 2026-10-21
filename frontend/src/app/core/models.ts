/**
 * Modelos de datos que expone el backend.
 *
 * <p>Reflejan uno a uno los record de Java: si el backend cambia un DTO, este
 * fichero es el unico que hay que tocar en el frontend.
 */

/** Pistas de practica. */
export type Track =
  | 'JAVA_CORE'
  | 'JAVA_OOP'
  | 'SQL'
  | 'SPRING_CORE'
  | 'SPRING_BOOT'
  | 'JPA'
  | 'SPRING_CLOUD'
  | 'SOAP'
  | 'REST'
  | 'TDD'
  | 'TESTING'
  | 'DEVOPS'
  | 'DESIGN';

/** Dificultad de un reto. */
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD' | 'EXPERT';

/** Estado de un elemento del catalogo frente a la persona que lo practica. */
export type ItemStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED';

/** Veredicto de la ejecucion de una solucion. */
export type SubmissionStatus =
  'PASSED' | 'TESTS_FAILED' | 'COMPILATION_ERROR' | 'TIMEOUT' | 'RUNTIME_ERROR';

export const TRACK_LABELS: Record<Track, string> = {
  JAVA_CORE: 'Java moderno',
  JAVA_OOP: 'OOP y patrones',
  SQL: 'SQL y Oracle',
  SPRING_CORE: 'Spring Core',
  SPRING_BOOT: 'Spring Boot',
  JPA: 'JPA / Hibernate',
  SPRING_CLOUD: 'Spring Cloud',
  SOAP: 'SOAP y legacy',
  REST: 'REST',
  TDD: 'TDD',
  TESTING: 'Testing',
  DEVOPS: 'DevOps',
  DESIGN: 'Diseno de sistemas',
};

export const DIFFICULTY_LABELS: Record<Difficulty, string> = {
  EASY: 'Facil',
  MEDIUM: 'Media',
  HARD: 'Dificil',
  EXPERT: 'Experto',
};

export interface KataSummary {
  id: string;
  track: Track;
  title: string;
  difficulty: Difficulty;
  tags: string[];
  xp: number;
  estimatedMinutes: number;
  status: ItemStatus;
  bestScore: number | null;
  attempts: number;
}

export interface KataDetail {
  id: string;
  track: Track;
  title: string;
  difficulty: Difficulty;
  tags: string[];
  xp: number;
  estimatedMinutes: number;
  prompt: string;
  functionSignature: string;
  starterCode: string;
  solutionClass: string;
  timeLimitSeconds: number;
  status: ItemStatus;
  bestScore: number | null;
  attempts: number;
}

export interface TestCaseResult {
  name: string;
  status: 'PASSED' | 'FAILED' | 'SKIPPED' | 'ABORTED';
  message: string | null;
}

export interface SubmissionResult {
  kataId: string;
  status: SubmissionStatus;
  cases: TestCaseResult[];
  passed: number;
  total: number;
  score: number;
  compilerOutput: string | null;
  programOutput: string | null;
  message: string;
  durationMs: number;
}

export interface TrackProgress {
  track: Track;
  completed: number;
  total: number;
}

export interface ProgressSummary {
  username: string;
  totalXp: number;
  level: number;
  levelProgress: number;
  xpToNextLevel: number;
  streakDays: number;
  completedItems: number;
  totalItems: number;
  byTrack: TrackProgress[];
  dailyQuestId: string | null;
  lastActivity: string | null;
}

export interface QuizQuestionView {
  id: string;
  topic: string;
  question: string;
  options: string[];
  difficulty: Difficulty;
  tags: string[];
  xp: number;
}

export interface QuizAnswerResult {
  correct: boolean;
  correctIndex: number;
  explanation: string;
  xpAwarded: number;
}

export interface DesignSummary {
  id: string;
  title: string;
  difficulty: Difficulty;
  track: Track;
  tags: string[];
  xp: number;
  estimatedMinutes: number;
}

export interface DesignDetail {
  id: string;
  title: string;
  question: string;
  scenario: string;
  requirements: string[];
  questions: string[];
  hints: string[];
  difficulty: Difficulty;
  track: Track;
  tags: string[];
  xp: number;
  estimatedMinutes: number;
}

/** Tipo de miembro que se puede completar tras un punto. */
export type CompletionKind = 'method' | 'field' | 'constant' | 'type';

/** Un miembro que se puede escribir despues de un punto. */
export interface CompletionItem {
  /** Texto que se muestra y que se inserta, por ejemplo `toString()`. */
  label: string;
  /** Tipo de miembro: metodo, campo, constante o tipo. */
  kind: CompletionKind;
  /** Firma legible, para distinguir sobrecargas. */
  detail: string;
  /** Texto exacto que se inserta en el editor. */
  insertText: string;
  /** Si el miembro es estatico. */
  isStatic: boolean;
  /** Si el miembro viene heredado de una superclase. */
  inherited: boolean;
}

/** Respuesta del autocompletado del editor. */
export interface CompletionResponse {
  /** Candidatos, ya filtrados por lo que se estaba escribiendo. */
  items: CompletionItem[];
  /** Posicion inicial del texto a sustituir, justo despues del punto. */
  replaceStart: number;
  /** Posicion final del texto a sustituir. */
  replaceEnd: number;
}
