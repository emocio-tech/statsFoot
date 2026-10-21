import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ChatApiService {
  private readonly http = inject(HttpClient);

  send(prompt: string): Observable<string> {
    return this.http.post('/api/chat', prompt, {
      headers: { 'Content-Type': 'text/plain' },
      responseType: 'text',
    });
  }
}
