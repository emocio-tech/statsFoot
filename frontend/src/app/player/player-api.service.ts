import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PlayerProfile, PlayerSearchResult } from './player.models';

@Injectable({ providedIn: 'root' })
export class PlayerApiService {
  private readonly http = inject(HttpClient);

  search(name: string): Observable<PlayerSearchResult> {
    return this.http.get<PlayerSearchResult>('/api/players', {
      params: new HttpParams().set('name', name),
    });
  }

  getProfile(id: number): Observable<PlayerProfile> {
    return this.http.get<PlayerProfile>(`/api/players/${id}`);
  }
}
