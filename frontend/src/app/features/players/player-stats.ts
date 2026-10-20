import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TournamentStatsService } from '../../core/services/tournament-stats.service';
import { PlayerTournamentStats } from '../../core/models/rating';
import { StatsColumn, StatsTable } from '../../shared/stats-table';

@Component({
  selector: 'app-player-stats',
  imports: [StatsTable],
  template: `
    <h2>Joueurs</h2>
    <p class="muted">
      Totaux sur l'ensemble du tournoi, pour chaque joueur entré en jeu. Note : moyenne des notes de match
      pondérée par les minutes jouées. Cliquer sur un en-tête pour trier.
    </p>

    @if (stats() !== undefined && !hasDetailedStats()) {
      <p class="notice">
        Fautes, coups francs, duels, corners, hors-jeu, distance et homme du match ne sont pas encore importés :
        ils s'affichent « – ».
      </p>
    }

    <div class="filters">
      <label>
        Rechercher
        <input type="search" placeholder="Joueur ou équipe" (input)="search.set($any($event.target).value)" />
      </label>
      <label>
        Matchs joués min.
        <select (change)="minMatches.set(+$any($event.target).value)">
          @for (n of [1, 3, 5, 7]; track n) {
            <option [value]="n">{{ n }}</option>
          }
        </select>
      </label>
      <span class="muted">{{ filtered().length }} joueurs</span>
    </div>

    <app-stats-table
      [rows]="filtered()"
      [columns]="columns"
      rowKey="playerId"
      [emptyText]="stats() === undefined ? 'Chargement…' : 'Aucun joueur.'"
    />
  `,
  styles: `
    .notice { padding: 0.5rem 0.75rem; border-left: 3px solid var(--accent); background: var(--surface); }
    .filters { display: flex; flex-wrap: wrap; align-items: center; gap: 1rem; margin: 1rem 0; font-size: 0.9rem; }
    .filters label { color: var(--muted); }
    input, select { margin-left: 0.3rem; font: inherit; }
  `,
})
export class PlayerStats {
  private readonly statsService = inject(TournamentStatsService);

  protected readonly stats = toSignal(this.statsService.getPlayerStats());
  protected readonly search = signal('');
  protected readonly minMatches = signal(1);

  protected readonly filtered = computed(() => {
    const term = this.search().trim().toLocaleLowerCase('fr');
    return (this.stats() ?? []).filter(
      (p) =>
        p.matchesPlayed >= this.minMatches() &&
        (term === '' || p.player.toLocaleLowerCase('fr').includes(term) || p.team.toLocaleLowerCase('fr').includes(term)),
    );
  });

  protected readonly hasDetailedStats = computed(() => (this.stats() ?? []).some((p) => p.detailedStatsMatches > 0));

  protected readonly columns: StatsColumn<PlayerTournamentStats>[] = [
    { key: 'player', label: 'Joueur', kind: 'text', sub: 'team' },
    { key: 'matchesPlayed', label: 'MJ', title: 'Matchs joués', kind: 'int' },
    { key: 'minutesPlayed', label: 'Min', title: 'Minutes jouées', kind: 'int' },
    { key: 'goals', label: 'Buts', title: 'Buts marqués (hors contre son camp)', kind: 'int' },
    { key: 'yellowCards', label: 'CJ', title: 'Cartons jaunes', kind: 'int' },
    { key: 'redCards', label: 'CR', title: 'Cartons rouges (dont deuxième jaune)', kind: 'int' },
    { key: 'foulsCommitted', label: 'Fautes', title: 'Fautes commises', kind: 'int' },
    { key: 'freeKicksWon', label: 'CF obt.', title: 'Coups francs obtenus', kind: 'int' },
    { key: 'freeKicksConceded', label: 'CF conc.', title: 'Coups francs concédés', kind: 'int' },
    { key: 'duelsWon', label: 'Duels G', title: 'Duels gagnés', kind: 'int' },
    { key: 'duelsLost', label: 'Duels P', title: 'Duels perdus', kind: 'int' },
    { key: 'corners', label: 'Corners', title: 'Corners tirés', kind: 'int' },
    { key: 'offsides', label: 'HJ', title: 'Hors-jeu', kind: 'int' },
    { key: 'distanceKm', label: 'Km', title: 'Distance totale parcourue (km)', kind: 'km' },
    { key: 'manOfTheMatchCount', label: 'HdM', title: 'Homme du match (nombre de fois)', kind: 'motm' },
    { key: 'ratingWeighted', label: 'Note', title: 'Note globale du tournoi', kind: 'rating' },
  ];
}
