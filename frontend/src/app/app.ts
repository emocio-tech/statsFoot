import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header>
      <h1>Analyse Foot</h1>
      <nav>
        <a routerLink="/matchs" routerLinkActive="active">Matchs</a>
        <a routerLink="/classements" routerLinkActive="active">Classements</a>
        <a routerLink="/joueurs" routerLinkActive="active">Joueurs</a>
        <a routerLink="/equipes" routerLinkActive="active">Équipes</a>
      </nav>
    </header>
    <main>
      <router-outlet />
    </main>
  `,
  styles: `
    header {
      display: flex; align-items: center; gap: 2rem; flex-wrap: wrap;
      padding: 0.75rem 1rem; background: #0b6e4f; color: #fff;
    }
    h1 { margin: 0; font-size: 1.4rem; }
    nav { display: flex; gap: 1rem; }
    nav a { color: #d6efe5; text-decoration: none; padding: 0.2rem 0; border-bottom: 2px solid transparent; }
    nav a.active { color: #fff; border-bottom-color: #fff; }
    main { max-width: 72rem; margin: 0 auto; padding: 1rem; }
  `,
})
export class App {}
