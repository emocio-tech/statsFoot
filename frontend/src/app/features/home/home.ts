import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { StatusService } from '../../core/services/status.service';

@Component({
  selector: 'app-home',
  template: `
    @if (status(); as s) {
      <p>Backend <strong>{{ s.application }}</strong> : {{ s.status }}</p>
    } @else {
      <p>Connexion au backend…</p>
    }
  `,
})
export class Home {
  readonly status = toSignal(inject(StatusService).getStatus());
}
