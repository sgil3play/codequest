import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import {
  DIFFICULTY_LABELS,
  Difficulty,
  ItemStatus,
  KataSummary,
  TRACK_LABELS,
  Track,
} from '../../core/models';

@Component({
  selector: 'app-katas',
  imports: [RouterLink],
  templateUrl: './katas.html',
  styleUrl: './katas.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Katas {
  private readonly api = inject(ApiService);

  protected readonly katas = toSignal(this.api.listKatas(), { initialValue: [] as KataSummary[] });

  protected readonly trackLabels = TRACK_LABELS;
  protected readonly difficultyLabels = DIFFICULTY_LABELS;

  protected readonly tracks = Object.keys(TRACK_LABELS) as Track[];

  protected readonly selectedTrack = signal<Track | 'ALL'>('ALL');
  protected readonly selectedStatus = signal<ItemStatus | 'ALL'>('ALL');

  protected readonly filtered = computed(() => {
    const track = this.selectedTrack();
    const status = this.selectedStatus();
    return this.katas().filter((k) => {
      if (track !== 'ALL' && k.track !== track) {
        return false;
      }
      if (status !== 'ALL' && k.status !== status) {
        return false;
      }
      return true;
    });
  });

  protected readonly counts = computed(() => {
    const list = this.katas();
    return {
      total: list.length,
      completed: list.filter((k) => k.status === 'COMPLETED').length,
      inProgress: list.filter((k) => k.status === 'IN_PROGRESS').length,
    };
  });

  protected setTrack(track: Track | 'ALL'): void {
    this.selectedTrack.set(track);
  }

  protected setStatus(status: ItemStatus | 'ALL'): void {
    this.selectedStatus.set(status);
  }

  protected statusLabel(status: ItemStatus): string {
    switch (status) {
      case 'COMPLETED':
        return 'Superado';
      case 'IN_PROGRESS':
        return 'En curso';
      default:
        return 'Sin empezar';
    }
  }

  protected statusClass(status: ItemStatus): string {
    switch (status) {
      case 'COMPLETED':
        return 'chip-ok';
      case 'IN_PROGRESS':
        return 'chip-warn';
      default:
        return '';
    }
  }

  protected difficultyClass(difficulty: Difficulty): string {
    switch (difficulty) {
      case 'EASY':
        return 'chip-ok';
      case 'MEDIUM':
        return 'chip-warn';
      case 'HARD':
        return 'chip-danger';
      default:
        return 'chip-accent';
    }
  }

  protected score(kata: KataSummary): string {
    if (kata.bestScore === null || kata.bestScore === undefined) {
      return '';
    }
    return `${Math.round(kata.bestScore * 100)}%`;
  }
}
