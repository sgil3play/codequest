import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, catchError, debounceTime, firstValueFrom, map, of, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { MarkdownPipe } from '../../core/markdown.pipe';
import {
  CompletionItem,
  CompletionKind,
  CompletionResponse,
  DIFFICULTY_LABELS,
  SubmissionResult,
  TRACK_LABELS,
} from '../../core/models';

@Component({
  selector: 'app-kata-detail',
  imports: [RouterLink, MarkdownPipe],
  templateUrl: './kata-detail.html',
  styleUrl: './kata-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class KataDetail {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  protected readonly difficultyLabels = DIFFICULTY_LABELS;
  protected readonly trackLabels = TRACK_LABELS;

  protected readonly kata = toSignal(
    this.route.paramMap.pipe(
      map((params) => params.get('id') ?? ''),
      switchMap((id) => this.api.getKata(id)),
    ),
    { initialValue: null },
  );

  /** Codigo que se envia al backend. */
  protected readonly code = signal('');
  /** Id del kata cuyo codigo inicial ya se ha volcado en el editor. */
  private readonly loadedFor = signal<string | null>(null);

  protected readonly result = signal<SubmissionResult | null>(null);
  protected readonly running = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly solution = signal<string | null>(null);
  protected readonly showingSolution = signal(false);

  /* --- Autocompletado ------------------------------------------------------ */

  /** Candidatos que se están ofreciendo. */
  protected readonly completions = signal<CompletionItem[]>([]);
  /** Si el desplegable esta visible. */
  protected readonly completionOpen = signal(false);
  /** Candidato resaltado, para poder moverse con las flechas. */
  protected readonly completionIndex = signal(0);
  /** Posicion del desplegable, en pixeles dentro del editor. */
  protected readonly completionPosition = signal({ top: 0, left: 0 });

  private readonly editor = viewChild<ElementRef<HTMLTextAreaElement>>('editor');
  private readonly mirror = viewChild<ElementRef<HTMLDivElement>>('mirror');
  private readonly completionList = viewChild<ElementRef<HTMLUListElement>>('completionList');
  private readonly editorWrap = viewChild<ElementRef<HTMLDivElement>>('editorWrap');

  /** Rango de texto que se sustituye al aceptar una sugerencia. */
  private readonly replaceRange = signal<{ start: number; end: number } | null>(null);

  /**
   * Peticiones de autocompletado.
   *
   * <p>Se encolan en un `Subject` en vez de llamar al backend en cada
   * pulsacion: el debounce evita preguntar por cada tecla y el `switchMap`
   * descarta las respuestas que llegan tarde, que ya no corresponden al
   * codigo que hay en el editor.
   */
  private readonly completionRequests = new Subject<{
    id: string;
    code: string;
    offset: number;
  }>();

  /** Veredicto global, para pintar la cabecera del panel de resultados. */
  protected readonly verdict = computed(() => {
    const r = this.result();
    if (!r) {
      return null;
    }
    switch (r.status) {
      case 'PASSED':
        return { label: 'Superado', chip: 'chip-ok' };
      case 'TESTS_FAILED':
        return { label: 'Tests fallidos', chip: 'chip-warn' };
      case 'COMPILATION_ERROR':
        return { label: 'Error de compilacion', chip: 'chip-danger' };
      case 'TIMEOUT':
        return { label: 'Tiempo agotado', chip: 'chip-danger' };
      default:
        return { label: 'Error en tiempo de ejecucion', chip: 'chip-danger' };
    }
  });

  constructor() {
    this.completionRequests
      .pipe(
        debounceTime(180),
        switchMap((request) =>
          this.api.complete(request.id, request.code, request.offset).pipe(
            map((response) => ({ request, response })),
            catchError(() => of(null)),
          ),
        ),
      )
      .subscribe((result) => {
        if (result) {
          this.applyCompletions(result.request, result.response);
        } else {
          this.closeCompletions();
        }
      });
  }

  /**
   * Vuelca el codigo inicial en el editor la primera vez que se carga cada
   * kata, sin pisar lo que la persona ya habia escrito.
   */
  private readonly syncStarterCode = effect(() => {
    const kata = this.kata();
    if (kata && this.loadedFor() !== kata.id) {
      this.loadedFor.set(kata.id);
      this.code.set(kata.starterCode);
    }
  });

  /**
   * Mantiene el textarea sincronizado con la senal de codigo.
   *
   * <p>El valor no se escribe siempre: solo cuando difiere de lo que ya hay
   * dentro. Escribirlo siempre devolveria el cursor al final del texto en
   * cada pulsacion, que es justo lo que rompe a un editor.
   */
  private readonly syncEditor = effect(() => {
    const value = this.code();
    const textarea = this.editor()?.nativeElement;
    if (textarea && textarea.value !== value) {
      textarea.value = value;
    }
  });

  protected onCodeChange(event: Event): void {
    const textarea = event.target as HTMLTextAreaElement;
    const value = textarea.value;
    this.code.set(value);

    const cursor = textarea.selectionStart ?? value.length;
    const typed = value.charAt(cursor - 1);
    // Se pregunta al backend cuando se escribe un punto o mientras la lista
    // esta abierta, para que se vaya filtrando sola.
    if (typed !== '.' && !this.completionOpen()) {
      this.closeCompletions();
      return;
    }
    this.requestCompletion(value, cursor);
  }

  protected onEditorKeydown(event: KeyboardEvent): void {
    // Ctrl+Espacio pide las sugerencias aunque no se haya escrito un punto.
    if (event.ctrlKey && event.key === ' ') {
      event.preventDefault();
      const textarea = event.target as HTMLTextAreaElement;
      this.requestCompletion(textarea.value, textarea.selectionStart ?? textarea.value.length);
      return;
    }

    if (!this.completionOpen()) {
      return;
    }

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.moveCompletion(1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.moveCompletion(-1);
        break;
      case 'Enter':
      case 'Tab':
        event.preventDefault();
        this.acceptCompletion();
        break;
      case 'Escape':
        event.preventDefault();
        this.closeCompletions();
        break;
      default:
        break;
    }
  }

  protected onCompletionPick(index: number, event: MouseEvent): void {
    // Se cancela el evento para que el textarea no pierda el foco.
    event.preventDefault();
    this.acceptCompletion(index);
  }

  protected closeCompletions(): void {
    if (!this.completionOpen()) {
      return;
    }
    this.completionOpen.set(false);
    this.completions.set([]);
    this.completionIndex.set(0);
    this.replaceRange.set(null);
  }

  protected updateCompletionPosition(): void {
    if (!this.completionOpen()) {
      return;
    }
    const caret = this.caretPosition();
    const wrap = this.editorWrap()?.nativeElement;
    if (!caret || !wrap) {
      return;
    }
    const list = this.completionList()?.nativeElement;
    const listHeight = list ? list.offsetHeight : 240;
    const maxTop = wrap.clientHeight - listHeight - 6;

    // Si la lista no cabe por abajo, se abre hacia arriba del cursor.
    const top =
      caret.top + listHeight > wrap.clientHeight
        ? Math.max(4, caret.top - listHeight - 24)
        : caret.top;
    const left = Math.min(caret.left, Math.max(4, wrap.clientWidth - 280));

    this.completionPosition.set({ top, left });
  }

  protected kindLabel(kind: CompletionKind): string {
    switch (kind) {
      case 'method':
        return 'M';
      case 'field':
        return 'F';
      case 'constant':
        return 'C';
      case 'type':
        return 'T';
    }
  }

  private requestCompletion(code: string, offset: number): void {
    const kata = this.kata();
    if (!kata) {
      return;
    }
    this.completionRequests.next({ id: kata.id, code, offset });
  }

  private applyCompletions(
    request: { id: string; code: string; offset: number },
    response: CompletionResponse,
  ): void {
    // Si el codigo ha cambiado mientras esperaba la respuesta, la peticion
    // nueva lo traera: esta ya no vale.
    if (this.code() !== request.code) {
      return;
    }
    if (!response.items.length) {
      this.closeCompletions();
      return;
    }
    this.completions.set(response.items);
    this.completionIndex.set(0);
    this.replaceRange.set({ start: response.replaceStart, end: response.replaceEnd });
    this.completionOpen.set(true);
    this.updateCompletionPosition();
  }

  private moveCompletion(delta: number): void {
    const total = this.completions().length;
    if (!total) {
      return;
    }
    const next = (this.completionIndex() + delta + total) % total;
    this.completionIndex.set(next);
    this.scrollCompletionIntoView();
  }

  private scrollCompletionIntoView(): void {
    const list = this.completionList()?.nativeElement;
    const item = list?.children[this.completionIndex()] as HTMLElement | undefined;
    // `scrollIntoView` no existe en todos los entornos (jsdom, por ejemplo).
    item?.scrollIntoView?.({ block: 'nearest' });
  }

  private acceptCompletion(index = this.completionIndex()): void {
    const item = this.completions()[index];
    const range = this.replaceRange();
    if (!item || !range) {
      this.closeCompletions();
      return;
    }

    const current = this.code();
    const next = current.slice(0, range.start) + item.insertText + current.slice(range.end);

    // El textarea se escribe antes que la senal: el efecto que las sincroniza
    // solo escribe cuando el valor difiere, asi que si la senal va primero el
    // efecto reescribe el texto y el navegador manda el cursor al final.
    const textarea = this.editor()?.nativeElement;
    const caret = range.start + item.insertText.length;
    if (textarea) {
      textarea.value = next;
      textarea.focus();
      textarea.setSelectionRange(caret, caret);
    }
    this.code.set(next);
    this.closeCompletions();
  }

  /**
   * Calcula en que pixel esta el cursor.
   *
   * <p>Un textarea no dice donde esta el cursor, solo en que caracter. La
   * tecnica es la del espejo: se copia el texto hasta el cursor en un div
   * invisible con las mismas metricas de fuente, se marca el final con un
   * span y se mide donde ha quedado la marca.
   */
  private caretPosition(): { top: number; left: number } | null {
    const textarea = this.editor()?.nativeElement;
    const mirror = this.mirror()?.nativeElement;
    if (!textarea || !mirror) {
      return null;
    }

    const style = getComputedStyle(textarea);
    const target = mirror.style;
    target.fontFamily = style.fontFamily;
    target.fontSize = style.fontSize;
    target.fontWeight = style.fontWeight;
    target.fontStyle = style.fontStyle;
    target.lineHeight = style.lineHeight;
    target.letterSpacing = style.letterSpacing;
    target.tabSize = style.tabSize;
    target.padding = style.padding;
    target.border = style.border;
    target.boxSizing = style.boxSizing;
    target.whiteSpace = style.whiteSpace;
    target.wordWrap = style.wordWrap;
    target.overflowWrap = style.overflowWrap;
    target.width = `${textarea.offsetWidth}px`;

    mirror.textContent = textarea.value.substring(0, textarea.selectionStart ?? 0);
    const marker = document.createElement('span');
    marker.textContent = '\u200b';
    mirror.appendChild(marker);

    return {
      top: marker.offsetTop - textarea.scrollTop,
      left: marker.offsetLeft - textarea.scrollLeft,
    };
  }

  protected async run(): Promise<void> {
    const kata = this.kata();
    if (!kata || this.running()) {
      return;
    }
    this.running.set(true);
    this.error.set(null);
    try {
      this.result.set(await firstValueFrom(this.api.submit(kata.id, this.code())));
      this.showingSolution.set(false);
    } catch (err) {
      this.error.set(describeError(err));
      this.result.set(null);
    } finally {
      this.running.set(false);
    }
  }

  protected async revealSolution(): Promise<void> {
    const kata = this.kata();
    if (!kata) {
      return;
    }
    try {
      this.solution.set(await firstValueFrom(this.api.revealSolution(kata.id)));
      this.showingSolution.set(true);
    } catch (err) {
      this.error.set(describeError(err));
    }
  }

  protected reset(): void {
    const kata = this.kata();
    if (kata) {
      // Igual que al aceptar una sugerencia: primero el textarea, luego la
      // senal, para que el efecto de sincronizacion no reescriba el texto.
      const textarea = this.editor()?.nativeElement;
      if (textarea) {
        textarea.value = kata.starterCode;
      }
      this.code.set(kata.starterCode);
      this.result.set(null);
      this.error.set(null);
      this.showingSolution.set(false);
      this.closeCompletions();
    }
  }

  protected caseIcon(status: string): string {
    switch (status) {
      case 'PASSED':
        return 'OK';
      case 'FAILED':
        return 'KO';
      case 'SKIPPED':
        return '--';
      default:
        return '??';
    }
  }

  protected caseClass(status: string): string {
    switch (status) {
      case 'PASSED':
        return 'case-ok';
      case 'FAILED':
        return 'case-ko';
      case 'SKIPPED':
        return 'case-skip';
      default:
        return 'case-abort';
    }
  }
}

function describeError(err: unknown): string {
  if (err && typeof err === 'object' && 'error' in err) {
    const payload = (err as { error: { message?: string } }).error;
    if (payload?.message) {
      return payload.message;
    }
  }
  return 'No se pudo completar la peticion. Revisa que el backend este arrancado.';
}
