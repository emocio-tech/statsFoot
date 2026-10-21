import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { TournamentStatsService } from '../../core/services/tournament-stats.service';
import { TeamTournamentStats } from '../../core/models/rating';
import { StatsColumn, StatsTable, markFictitious } from '../../shared/stats-table';

@Component({
  selector: 'app-team-stats',
  imports: [StatsTable],
  template: `
    <h2>Équipes</h2>
    <p class="muted">
      Totaux sur l'ensemble du tournoi. Note : moyenne des notes de match de l'équipe.
      Cliquer sur un en-tête pour trier, sur une ligne pour ouvrir le rapport.
    </p>

    <app-stats-table
      [rows]="stats() ?? []"
      [columns]="columns()"
      rowKey="teamId"
      [clickable]="true"
      (rowClick)="open($event)"
      [emptyText]="stats() === undefined ? 'Chargement…' : 'Aucune équipe.'"
    />
    <p class="legend muted">* données fictives : générées pour la démonstration, non issues des matchs réels.</p>
  `,
  styles: `
    .legend { font-size: 0.85rem; margin-top: 0.5rem; }
    app-stats-table { display: block; margin-top: 1rem; }
  `,
})
export class TeamStats {
  private readonly statsService = inject(TournamentStatsService);
  private readonly router = inject(Router);

  protected readonly stats = toSignal(this.statsService.getTeamStats());


  private readonly baseColumns: StatsColumn<TeamTournamentStats>[] = [
    { key: 'team', label: 'Équipe', kind: 'text' },
    { key: 'matchesPlayed', label: 'MJ', title: 'Matchs joués', kind: 'int' },
    { key: 'goalsFor', label: 'Buts', title: 'Buts marqués', kind: 'int' },
    { key: 'goalsAgainst', label: 'Enc.', title: 'Buts encaissés', kind: 'int' },
    { key: 'yellowCards', label: 'CJ', title: 'Cartons jaunes', kind: 'int' },
    { key: 'redCards', label: 'CR', title: 'Cartons rouges (dont deuxième jaune)', kind: 'int' },
    { key: 'foulsCommitted', label: 'Fautes', title: 'Fautes commises', kind: 'int' },
    { key: 'freeKicksWon', label: 'CF obt.', title: 'Coups francs obtenus', kind: 'int' },
    { key: 'freeKicksConceded', label: 'CF conc.', title: 'Coups francs concédés', kind: 'int' },
    { key: 'duelsWon', label: 'Duels G', title: 'Duels gagnés', kind: 'int' },
    { key: 'duelsLost', label: 'Duels P', title: 'Duels perdus', kind: 'int' },
    { key: 'corners', label: 'Corners', title: 'Corners obtenus', kind: 'int' },
    { key: 'offsides', label: 'HJ', title: 'Hors-jeu', kind: 'int' },
    { key: 'distanceKm', label: 'Km', title: 'Distance totale parcourue (km)', kind: 'km' },
    { key: 'rating', label: 'Note', title: 'Note globale du tournoi', kind: 'rating' },
  ];

  protected readonly columns = computed(() => markFictitious(this.baseColumns, this.stats()?.[0]?.fictitiousFields));

  protected open(row: TeamTournamentStats): void {
    this.router.navigate(['/equipes', row.teamId]);
  }
}
