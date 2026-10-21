import { Component, computed, inject, input, numberAttribute } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, of, startWith, switchMap } from 'rxjs';
import { AnalyseService } from '../../core/services/analyse.service';
import { PlayerReport as Report, PlayerTotals } from '../../core/models/analyse';
import { RatingBadge } from '../../shared/rating-badge';
import { StageLabelPipe } from '../../shared/stage-label.pipe';

interface Stat {
  label: string;
  value: string;
  fictitious: boolean;
}

/** Rapport complet d'un joueur : route /joueurs/:id, ou sous une réponse de l'onglet Analyse. */
@Component({
  selector: 'app-player-report',
  imports: [DatePipe, RouterLink, RatingBadge, StageLabelPipe],
  template: `
    @let r = report();
    @if (r === undefined) {
      <p class="muted">Chargement du rapport…</p>
    } @else if (r === null) {
      <p class="muted">Joueur introuvable.</p>
    } @else {
      <article>
        <header>
          <h3>{{ r.name }}</h3>
          <span class="muted">
            <a [routerLink]="['/equipes', r.teamId]">{{ r.team }}</a>
            @if (r.goalkeeper) { · gardien }
          </span>
          <app-rating-badge [value]="r.totals.ratingWeighted" />
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

        <h4>Matchs</h4>
        <div class="scroll">
          <table>
            <thead>
              <tr>
                <th scope="col">Date</th><th scope="col">Phase</th><th scope="col">Adversaire</th>
                <th scope="col" class="num">Score</th><th scope="col" class="num">Min</th>
                <th scope="col" class="num">Buts</th><th scope="col" class="num" title="Passes décisives (données fictives)">PD*</th>
                <th scope="col" class="num">CJ</th><th scope="col" class="num">Note</th>
              </tr>
            </thead>
            <tbody>
              @for (m of r.matches; track m.matchId) {
                <tr [class.best]="m.matchId === r.bestMatch?.matchId">
                  <td><a [routerLink]="['/matchs', m.matchId]">{{ m.kickoffAt | date: 'd MMM' }}</a></td>
                  <td>{{ m.stage | stageLabel }}</td>
                  <td>{{ m.opponent }}</td>
                  <td class="num">{{ m.score }}</td>
                  <td class="num">{{ m.minutesPlayed }}</td>
                  <td class="num">{{ m.goals }}</td>
                  <td class="num">{{ m.assists }}</td>
                  <td class="num">{{ m.yellowCards }}@if (m.sentOff) { · exclu }</td>
                  <td class="num"><app-rating-badge [value]="m.rating" /></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        @if (r.bestMatch; as b) {
          <p class="muted">Meilleur match : contre {{ b.opponent }} ({{ b.score }}), note {{ b.rating }}.</p>
        }

        <h4>Rang sur chaque critère</h4>
        <ul class="ranks">
          @for (k of r.ranks; track k.questionId) {
            <li>
              {{ k.label }} :
              @if (k.rank === null) {
                <span class="muted">non classé</span>
              } @else {
                <strong>{{ k.rank }}<sup>{{ k.rank === 1 ? 'er' : 'e' }}</sup></strong> sur {{ k.outOf }}
              }
            </li>
          }
        </ul>
      </article>
    }
  `,
  styles: `
    header { display: flex; align-items: baseline; gap: 0.8rem; flex-wrap: wrap; }
    h3 { margin: 0; font-size: 1.3rem; }
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
    tr.best td { background: var(--hover); }
    .num { text-align: right; font-variant-numeric: tabular-nums; }
    .ranks { margin: 0; padding-left: 1.2rem; }
  `,
})
export class PlayerReport {
  private readonly analyseService = inject(AnalyseService);

  /** Lié au paramètre de route :id, ou fourni par l'onglet Analyse. */
  readonly id = input.required({ transform: numberAttribute });

  /** undefined pendant le chargement, null si introuvable. */
  protected readonly report = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) =>
        this.analyseService.getPlayerReport(id).pipe(
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
    const stat = (label: string, key: keyof PlayerTotals, suffix = ''): Stat => ({
      label,
      value: display(t[key], suffix),
      fictitious: fictitious.has(key),
    });
    return [
      stat('Matchs joués', 'matchesPlayed'),
      stat('Titularisations', 'matchesStarted'),
      stat('Minutes', 'minutesPlayed'),
      stat('Buts', 'goals'),
      stat('dont penalties', 'penaltyGoals'),
      stat('Buts décisifs', 'decisiveGoals'),
      stat('Passes décisives', 'assists'),
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
      stat('Homme du match', 'manOfTheMatchCount'),
      stat('Note moyenne', 'rating'),
    ];
  });
}

/** Valeur affichée ; une donnée inconnue s'affiche « – », jamais 0. */
export function display(value: unknown, suffix = ''): string {
  if (value === null || value === undefined) return '–';
  return typeof value === 'number' ? `${value.toLocaleString('fr-FR')}${suffix}` : `${value}${suffix}`;
}
