import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { MarkdownPipe } from '../../core/markdown.pipe';
import { DIFFICULTY_LABELS, QuizAnswerResult, QuizQuestionView } from '../../core/models';

interface AnswerState {
  selected: number | null;
  result: QuizAnswerResult | null;
}

@Component({
  selector: 'app-quizzes',
  imports: [MarkdownPipe],
  templateUrl: './quizzes.html',
  styleUrl: './quizzes.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Quizzes {
  private readonly api = inject(ApiService);

  protected readonly difficultyLabels = DIFFICULTY_LABELS;

  /** Preguntas agrupadas por tema, tal y como las devuelve el backend. */
  protected readonly topics = toSignal(this.api.listQuizzes(), {
    initialValue: {} as Record<string, QuizQuestionView[]>,
  });

  /** Nombres de los temas, en el orden en que los devuelve el backend. */
  protected readonly topicNames = computed(() => Object.keys(this.topics()));

  /** Respuestas ya enviadas, indexadas por id de pregunta. */
  protected readonly answers = signal<Record<string, AnswerState>>({});

  protected readonly openTopics = signal<Set<string>>(new Set());

  protected readonly total = computed(() =>
    Object.values(this.topics()).reduce((acc, list) => acc + list.length, 0),
  );

  protected readonly answered = computed(
    () => Object.values(this.answers()).filter((state) => state.result !== null).length,
  );

  protected readonly correct = computed(
    () => Object.values(this.answers()).filter((state) => state.result?.correct).length,
  );

  protected toggleTopic(topic: string): void {
    this.openTopics.update((current) => {
      const next = new Set(current);
      if (next.has(topic)) {
        next.delete(topic);
      } else {
        next.add(topic);
      }
      return next;
    });
  }

  protected isOpen(topic: string): boolean {
    return this.openTopics().has(topic);
  }

  protected answerOf(question: QuizQuestionView): AnswerState {
    return this.answers()[question.id] ?? { selected: null, result: null };
  }

  protected select(question: QuizQuestionView, index: number): void {
    const current = this.answerOf(question);
    if (current.result) {
      return;
    }
    this.answers.update((all) => ({
      ...all,
      [question.id]: { ...current, selected: index },
    }));
  }

  protected async submit(question: QuizQuestionView): Promise<void> {
    const current = this.answerOf(question);
    if (current.selected === null || current.result) {
      return;
    }
    try {
      const result = await firstValueFrom(this.api.answerQuiz(question.id, current.selected));
      this.answers.update((all) => ({
        ...all,
        [question.id]: { selected: current.selected, result },
      }));
    } catch {
      // Si el backend falla se deja la seleccion tal cual: se puede reintentar.
    }
  }

  protected optionClass(question: QuizQuestionView, index: number): string {
    const state = this.answerOf(question);
    const result = state.result;
    if (!result) {
      return state.selected === index ? 'option selected' : 'option';
    }
    if (index === result.correctIndex) {
      return 'option correct';
    }
    if (index === state.selected) {
      return 'option wrong';
    }
    return 'option';
  }

  protected optionMark(question: QuizQuestionView, index: number): string {
    const state = this.answerOf(question);
    const result = state.result;
    if (!result) {
      return '';
    }
    if (index === result.correctIndex) {
      return 'correcta';
    }
    if (index === state.selected) {
      return 'tu respuesta';
    }
    return '';
  }
}
