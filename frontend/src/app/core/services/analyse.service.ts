import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AnalyseAnswer, AnalyseQuestion, AssistantStatus, PlayerReport, TeamReport } from '../models/analyse';

/** Questions et réponses de l'onglet Analyse. */
@Injectable({ providedIn: 'root' })
export class AnalyseService {
  private readonly http = inject(HttpClient);

  getQuestions(): Observable<AnalyseQuestion[]> {
    return this.http.get<AnalyseQuestion[]>('/api/analyse/questions');
  }

  answer(questionId: string): Observable<AnalyseAnswer> {
    return this.http.get<AnalyseAnswer>(`/api/analyse/${encodeURIComponent(questionId)}`);
  }

  getAssistantStatus(): Observable<AssistantStatus> {
    return this.http.get<AssistantStatus>('/api/analyse/assistant');
  }

  /** Question libre : mots-clés, puis assistant (tool Claude rank) s'ils échouent. */
  search(question: string): Observable<AnalyseAnswer> {
    return this.http.get<AnalyseAnswer>('/api/analyse', { params: { q: question } });
  }

  getPlayerReport(id: number): Observable<PlayerReport> {
    return this.http.get<PlayerReport>(`/api/players/${id}/report`);
  }

  getTeamReport(id: number): Observable<TeamReport> {
    return this.http.get<TeamReport>(`/api/teams/${id}/report`);
  }
}
