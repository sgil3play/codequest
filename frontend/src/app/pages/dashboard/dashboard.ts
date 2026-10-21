import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { ProgressSummary, TRACK_LABELS } from '../../core/models';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Dashboard {
  private readonly api = inject(ApiService);

  protected readonly progress = toSignal(this.api.getProgress(), { initialValue: null });

  protected readonly trackLabels = TRACK_LABELS;

  /** Porcentaje de progreso global, redondeado. */
  protected readonly globalProgress = computed(() => {
    const p = this.progress();
    if (!p || p.totalItems === 0) {
      return 0;
    }
    return Math.round((p.completedItems / p.totalItems) * 100);
  });

  /** Pistas ordenadas: primero las que menos se han trabajado. */
  protected readonly tracks = computed(() => {
    const p = this.progress();
    if (!p) {
      return [];
    }
    return [...p.byTrack]
      .filter((t) => t.total > 0)
      .map((t) => ({
        ...t,
        label: TRACK_LABELS[t.track] ?? t.track,
        percent: Math.round((t.completed / t.total) * 100),
      }))
      .sort((a, b) => a.percent - b.percent);
  });

  protected summary(): ProgressSummary | null {
    return this.progress();
  }
}
