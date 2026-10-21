import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { DIFFICULTY_LABELS, TRACK_LABELS } from '../../core/models';

@Component({
  selector: 'app-design',
  imports: [RouterLink],
  templateUrl: './design.html',
  styleUrl: './design.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Design {
  private readonly api = inject(ApiService);

  protected readonly designs = toSignal(this.api.listDesigns(), { initialValue: [] });

  protected readonly difficultyLabels = DIFFICULTY_LABELS;
  protected readonly trackLabels = TRACK_LABELS;
}
