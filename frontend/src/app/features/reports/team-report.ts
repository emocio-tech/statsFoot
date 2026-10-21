import { Component, computed, inject, input, numberAttribute } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, of, startWith, switchMap } from 'rxjs';
import { AnalyseService } from '../../core/services/analyse.service';
import { SquadLine, TeamReport as Report, TeamTotals } from '../../core/models/analyse';
import { RatingBadge } from '../../shared/rating-badge';
import { StageLabelPipe } from '../../shared/stage-label.pipe';
import { StatsColumn, StatsTable } from '../../shared/stats-table';
import { display } from './player-report';

interface Stat {
  label: string;
  value: string;
  fictitious: boolean;
}

const POSITIONS: Record<number, string> = {
  1: 'Championne',
  2: 'Finaliste',
  3: '3e',
  4: '4e',
  5: 'Quart de finale',
  6: 'Huitième de finale',
  7: 'Seizième de finale',
  8: 'Phase de groupes',
};

/** Rapport complet d'une équipe : route /equipes/:id, ou sous une réponse de l'onglet Analyse. */
@Component({
  selector: 'app-team-report',
  imports: [DatePipe, RouterLink, RatingBadge, StageLabelPipe, StatsTable],
  template: `
    @let r = report();
    @if (r === undefined) {
      <p class="muted">Chargement du rapport…</p>
    } @else if (r === null) {
      <p class="muted">Équipe introuvable.</p>
    } @else {
      <article>
        <header>
          <h3>{{ r.team }}</h3>
          <span class="position">{{ position(r.totals.finalPosition) }}</span>
          <app-rating-badge [value]="r.totals.rating" />
        </header>

        <dl class="stats">
          @for (s of stats(); track s.label) {
            <div [class.fictitious]="s.fictitious">
              <dt>{{ s.label }}@if (s.fictitious) {<span aria-label="donnée fictive">*</span>}</dt>
              <dd>{{ s.value }}</dd>
            </div>
          }
        </dl>
        <p class="legend muted">* données fictives : générées pour la démonstration, non issues des matchs réels.</p>

        <h4>Parcours</h4>
        <div class="scroll">
          <table>
            <thead>
              <tr>
                <th scope="col">Date</th><th scope="col">Phase</th><th scope="col">Adversaire</th>
                <th scope="col" class="num">Score</th><th scope="col" class="num">Note</th>
              </tr>
            </thead>
            <tbody>
              @for (m of r.matches; track m.matchId) {
                <tr>
                  <td><a [routerLink]="['/matchs', m.matchId]">{{ m.kickoffAt | date: 'd MMM' }}</a></td>
                  <td>{{ m.stage | stageLabel }}</td>
                  <td>{{ m.opponent }}</td>
                  <td class="num">
                    {{ m.score }}
                    @if (m.penalties) { <small>(tab {{ m.penalties }})</small> }
                  </td>
                  <td class="num"><app-rating-badge [value]="m.rating" /></td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <p>
          @if (r.topScorer; as s) { Meilleur buteur : <strong>{{ s.name }}</strong> ({{ s.goals }} buts). }
          @if (r.bestPlayer; as b) { Meilleur joueur selon la note : <strong>{{ b.name }}</strong>. }
        </p>

        <h4>Effectif</h4>
        <app-stats-table [rows]="r.squad" [columns]="squadColumns" rowKey="playerId" [clickable]="true"
                         (rowClick)="openPlayer($event)" />
      </article>
    }
  `,
  styles: `
    header { display: flex; align-items: baseline; gap: 0.8rem; flex-wrap: wrap; }
    h3 { margin: 0; font-size: 1.3rem; }
    .position { font-weight: 600; color: var(--accent); }
    .stats { display: grid; grid-template-columns: repeat(auto-fill, minmax(9rem, 1fr)); gap: 0.5rem; margin: 1rem 0 0.3rem; }
    .stats div { padding: 0.4rem 0.6rem; border: 1px solid var(--border); border-radius: 0.4rem; background: var(--surface); }
    .stats div.fictitious { border-style: dashed; }
    dt { font-size: 0.8rem; color: var(--muted); }
    dt span { color: var(--accent); margin-left: 0.1em; }
    dd { margin: 0; font-weight: 600; font-variant-numeric: tabular-nums; }
    .legend { font-size: 0.85rem; margin: 0; }
    h4 { margin: 1.2rem 0 0.4rem; }
    .scroll { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; white-space: nowrap; }
    th { text-align: left; font-size: 0.8rem; color: var(--muted); font-weight: 600; padding: 0.3rem; }
    td { padding: 0.3rem; border-top: 1px solid var(--border); }
    .num { text-align: right; font-variant-numeric: tabular-nums; }
  `,
})
export class TeamReport {
  private readonly analyseService = inject(AnalyseService);
  private readonly router = inject(Router);

  /** Lié au paramètre de route :id, ou fourni par l'onglet Analyse. */
  readonly id = input.required({ transform: numberAttribute });

  /** undefined pendant le chargement, null si introuvable. */
  protected readonly report = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) =>
        this.analyseService.getTeamReport(id).pipe(
          catchError(() => of(null)),
          startWith(undefined),
        ),
      ),
    ),
    { initialValue: undefined as Report | null | undefined },
  );

  protected readonly stats = computed<Stat[]>(() => {
    const r = this.report();
    if (!r) return [];
    const fictitious = new Set(r.fictitiousFields);
    const t = r.totals;
    const stat = (label: string, key: keyof TeamTotals, suffix = ''): Stat => ({
      label,
      value: display(t[key], suffix),
      fictitious: fictitious.has(key),
    });
    return [
      stat('Matchs joués', 'matchesPlayed'),
      stat('Victoires', 'wins'),
      stat('Nuls', 'draws'),
      stat('Défaites', 'losses'),
      stat('Points', 'points'),
      stat('Buts marqués', 'goalsFor'),
      stat('Buts encaissés', 'goalsAgainst'),
      stat('Cartons jaunes', 'yellowCards'),
      stat('Cartons rouges', 'redCards'),
      stat('Points de fair-play', 'fairPlayPoints'),
      stat('Fautes commises', 'foulsCommitted'),
      stat('Coups francs obtenus', 'freeKicksWon'),
      stat('Coups francs concédés', 'freeKicksConceded'),
      stat('Duels gagnés', 'duelsWon'),
      stat('Duels perdus', 'duelsLost'),
      stat('Corners', 'corners'),
      stat('Hors-jeu', 'offsides'),
      stat('Distance', 'distanceKm', ' km'),
    ];
  });

  protected readonly squadColumns: StatsColumn<SquadLine>[] = [
    { key: 'name', label: 'Joueur', kind: 'text' },
    { key: 'matchesPlayed', label: 'MJ', title: 'Matchs joués', kind: 'int' },
    { key: 'minutesPlayed', label: 'Min', title: 'Minutes jouées', kind: 'int' },
    { key: 'goals', label: 'Buts', kind: 'int' },
    { key: 'assists', label: 'PD', title: 'Passes décisives', kind: 'int', fictitious: true },
    { key: 'yellowCards', label: 'CJ', title: 'Cartons jaunes', kind: 'int' },
    { key: 'redCards', label: 'CR', title: 'Cartons rouges', kind: 'int' },
    { key: 'ratingWeighted', label: 'Note', kind: 'rating' },
  ];

  protected position(finalPosition: number): string {
    return POSITIONS[finalPosition] ?? '';
  }

  protected openPlayer(player: SquadLine): void {
    this.router.navigate(['/joueurs', player.playerId]);
  }
}
