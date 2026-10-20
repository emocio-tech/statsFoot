import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PlayerTournamentStats, TeamTournamentStats } from '../models/rating';

/** Statistiques cumulées du tournoi le plus récent. */
@Injectable({ providedIn: 'root' })
export class TournamentStatsService {
  private readonly http = inject(HttpClient);

  getPlayerStats(): Observable<PlayerTournamentStats[]> {
    return this.http.get<PlayerTournamentStats[]>('/api/stats/players');
  }

  getTeamStats(): Observable<TeamTournamentStats[]> {
    return this.http.get<TeamTournamentStats[]>('/api/stats/teams');
  }
}
