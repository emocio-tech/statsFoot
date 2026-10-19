import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { StatusResponse } from '../models/status-response';

@Injectable({ providedIn: 'root' })
export class StatusService {
  private readonly http = inject(HttpClient);

  getStatus(): Observable<StatusResponse> {
    return this.http.get<StatusResponse>('/api/status');
  }
}
