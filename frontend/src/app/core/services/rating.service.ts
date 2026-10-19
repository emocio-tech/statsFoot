import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { MatchRatings, MatchSummary, PlayerRanking, TeamRanking } from '../models/rating';

@Injectable({ providedIn: 'root' })
export class RatingService {
  private readonly http = inject(HttpClient);

  getMatches(): Observable<MatchSummary[]> {
    return this.http.get<MatchSummary[]>('/api/matches');
  }

  getMatchRatings(id: number): Observable<MatchRatings> {
    return this.http.get<MatchRatings>(`/api/matches/${id}/ratings`);
  }

  getPlayerRanking(minMatches: number, limit: number): Observable<PlayerRanking[]> {
    const params = new HttpParams().set('minMatches', minMatches).set('limit', limit);
    return this.http.get<PlayerRanking[]>('/api/rankings/players', { params });
  }

  getTeamRanking(): Observable<TeamRanking[]> {
    return this.http.get<TeamRanking[]>('/api/rankings/teams');
  }
}
