import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom, map, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { MarkdownPipe } from '../../core/markdown.pipe';
import { DIFFICULTY_LABELS, SubmissionResult, TRACK_LABELS } from '../../core/models';

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

  protected onCodeChange(event: Event): void {
    this.code.set((event.target as HTMLTextAreaElement).value);
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
      this.code.set(kata.starterCode);
      this.result.set(null);
      this.error.set(null);
      this.showingSolution.set(false);
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
