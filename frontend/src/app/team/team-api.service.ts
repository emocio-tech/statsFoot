import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { TeamComparison, TeamSearchResult } from './team.models';

@Injectable({ providedIn: 'root' })
export class TeamApiService {
  private readonly http = inject(HttpClient);

  search(name: string): Observable<TeamSearchResult> {
    return this.http.get<TeamSearchResult>('/api/teams', {
      params: new HttpParams().set('name', name),
    });
  }

  compare(idA: number, idB: number): Observable<TeamComparison> {
    return this.http.get<TeamComparison>('/api/teams/compare', {
      params: new HttpParams().set('teamA', idA).set('teamB', idB),
    });
  }
}
