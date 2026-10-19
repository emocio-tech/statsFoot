import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: `
    <header>
      <h1>Analyse Foot</h1>
    </header>
    <main>
      <router-outlet />
    </main>
  `,
  styles: `
    header { padding: 1rem; background: #0b6e4f; color: #fff; }
    h1 { margin: 0; font-size: 1.5rem; }
    main { padding: 1rem; }
  `,
})
export class App {}
