import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  DesignDetail,
  DesignSummary,
  KataDetail,
  KataSummary,
  ProgressSummary,
  QuizAnswerResult,
  QuizQuestionView,
  SubmissionResult,
  Track,
} from './models';

/**
 * Cliente HTTP del backend.
 *
 * <p>Todo el trafico va contra {@code /api}, que en desarrollo redirige el
 * servidor de Angular al backend mediante el proxy de {@code proxy.conf.json}
 * y en produccion lo sirve el mismo origen. Asi la aplicacion no necesita
 * CORS ni configuracion por entorno.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  /** Perfil de la persona que practica. */
  private readonly user = 'sergio';

  // --- Retos de codigo ---------------------------------------------------

  listKatas(track?: Track): Observable<KataSummary[]> {
    let params = new HttpParams().set('user', this.user);
    if (track) {
      params = params.set('track', track);
    }
    return this.http.get<KataSummary[]>('/api/katas', { params });
  }

  getKata(id: string): Observable<KataDetail> {
    return this.http.get<KataDetail>(`/api/katas/${id}`, {
      params: new HttpParams().set('user', this.user),
    });
  }

  submit(id: string, code: string): Observable<SubmissionResult> {
    return this.http.post<SubmissionResult>(`/api/katas/${id}/submit`, {
      code,
      user: this.user,
    });
  }

  revealSolution(id: string): Observable<string> {
    return this.http.get(`/api/katas/${id}/solution`, { responseType: 'text' });
  }

  // --- Preguntas conceptuales -------------------------------------------

  listQuizzes(track?: Track): Observable<Record<string, QuizQuestionView[]>> {
    let params = new HttpParams();
    if (track) {
      params = params.set('track', track);
    }
    return this.http.get<Record<string, QuizQuestionView[]>>('/api/quizzes', { params });
  }

  answerQuiz(id: string, selectedIndex: number): Observable<QuizAnswerResult> {
    return this.http.post<QuizAnswerResult>(
      `/api/quizzes/${id}/answer`,
      { selectedIndex },
      {
        params: new HttpParams().set('user', this.user),
      },
    );
  }

  // --- Diseno de sistemas -------------------------------------------------

  listDesigns(): Observable<DesignSummary[]> {
    return this.http.get<DesignSummary[]>('/api/design');
  }

  getDesign(id: string): Observable<DesignDetail> {
    return this.http.get<DesignDetail>(`/api/design/${id}`);
  }

  revealDesignReference(id: string): Observable<string> {
    return this.http.get(`/api/design/${id}/reference`, { responseType: 'text' });
  }

  // --- Progreso ------------------------------------------------------------

  getProgress(): Observable<ProgressSummary> {
    return this.http.get<ProgressSummary>('/api/progress/summary', {
      params: new HttpParams().set('user', this.user),
    });
  }
}
