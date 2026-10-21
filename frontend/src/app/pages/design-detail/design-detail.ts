import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom, map, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { MarkdownPipe } from '../../core/markdown.pipe';
import { DIFFICULTY_LABELS } from '../../core/models';

@Component({
  selector: 'app-design-detail',
  imports: [RouterLink, MarkdownPipe],
  templateUrl: './design-detail.html',
  styleUrl: './design-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DesignDetail {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  protected readonly difficultyLabels = DIFFICULTY_LABELS;

  protected readonly design = toSignal(
    this.route.paramMap.pipe(
      map((params) => params.get('id') ?? ''),
      switchMap((id) => this.api.getDesign(id)),
    ),
    { initialValue: null },
  );

  /** Respuesta redactada por la persona, se guarda en local. */
  protected readonly notes = signal<string>(this.loadNotes());
  protected readonly reference = signal<string | null>(null);
  protected readonly loadingReference = signal(false);

  private loadNotes(): string {
    try {
      return localStorage.getItem(this.notesKey()) ?? '';
    } catch {
      return '';
    }
  }

  private notesKey(): string {
    return `codequest.design.notes.${this.design()?.id ?? 'unknown'}`;
  }

  protected onNotesChange(event: Event): void {
    const value = (event.target as HTMLTextAreaElement).value;
    this.notes.set(value);
    try {
      localStorage.setItem(this.notesKey(), value);
    } catch {
      // Si el almacenamiento no esta disponible se sigue trabajando igual.
    }
  }

  protected async revealReference(): Promise<void> {
    const design = this.design();
    if (!design || this.loadingReference()) {
      return;
    }
    this.loadingReference.set(true);
    try {
      this.reference.set(await firstValueFrom(this.api.revealDesignReference(design.id)));
    } finally {
      this.loadingReference.set(false);
    }
  }
}
