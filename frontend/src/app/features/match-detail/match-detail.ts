import { Component, computed, inject, input, numberAttribute } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, of, switchMap } from 'rxjs';
import { RatingService } from '../../core/services/rating.service';
import { MatchRatings, PlayerRating, TeamRating } from '../../core/models/rating';
import { RatingBadge } from '../../shared/rating-badge';
import { StageLabelPipe } from '../../shared/stage-label.pipe';

interface TeamSheet {
  rating: TeamRating | undefined;
  name: string;
  players: PlayerRating[];
}

const RESULT_LABELS = { W: 'Victoire', D: 'Nul', L: 'Défaite' } as const;

@Component({
  selector: 'app-match-detail',
  imports: [DatePipe, RouterLink, RatingBadge, StageLabelPipe],
  template: `
    <a routerLink="/matchs" class="back">← Tous les matchs</a>

    @if (data(); as d) {
      <header class="match-header">
        <p class="muted">
          {{ d.match.stage | stageLabel: d.match.groupName }} · {{ d.match.kickoffAt | date: 'EEEE d MMMM y, HH:mm' }}
          · {{ d.match.stadium }}, {{ d.match.city }}
        </p>
        <h2>
          {{ d.match.team1 }}
          <span class="score">{{ d.match.score1 }} – {{ d.match.score2 }}</span>
          {{ d.match.team2 }}
        </h2>
        @if (d.match.penalties1 !== null) {
          <p class="muted">Tirs au but : {{ d.match.penalties1 }} – {{ d.match.penalties2 }}</p>
        } @else if (d.match.extraTime) {
          <p class="muted">Après prolongation</p>
        }
      </header>

      <div class="sheets">
        @for (sheet of sheets(); track sheet.name) {
          <section class="sheet">
            <div class="team-line">
              <h3>{{ sheet.name }}</h3>
              <app-rating-badge [value]="sheet.rating?.rating ?? null" />
            </div>
            @if (sheet.rating; as r) {
              <p class="muted team-facts">
                {{ resultLabel(r) }} · {{ r.points }} pt{{ r.points > 1 ? 's' : '' }} · fair-play {{ r.fairPlayPoints }}
              </p>
            }

            <table>
              <thead>
                <tr>
                  <th scope="col">Joueur</th>
                  <th scope="col" class="num">Min</th>
                  <th scope="col">Faits</th>
                  <th scope="col" class="num">Note</th>
                </tr>
              </thead>
              <tbody>
                @for (p of sheet.players; track p.playerId) {
                  <tr [class.sub]="!p.starter">
                    <td>
                      {{ p.name }}
                      @if (p.captain) { <abbr title="Capitaine" class="tag">C</abbr> }
                      @if (!p.starter) { <span class="tag muted">remp.</span> }
                    </td>
                    <td class="num">{{ p.minutesPlayed }}'</td>
                    <td class="facts">
                      @for (g of range(p.goals); track $index) { <span title="But">⚽</span> }
                      @if (p.decisiveGoals > 0) { <span class="tag decisive" title="But décisif">décisif</span> }
                      @for (g of range(p.ownGoals); track $index) { <span class="tag og" title="But contre son camp">csc</span> }
                      @for (y of range(p.yellowCards); track $index) { <span class="card yellow" title="Carton jaune"></span> }
                      @if (p.sentOff) { <span class="card red" title="Expulsion"></span> }
                      @if (p.shootoutScored > 0) { <span class="tag" title="Tir au but réussi">tab ✓</span> }
                      @if (p.shootoutMissed > 0) { <span class="tag og" title="Tir au but raté">tab ✗</span> }
                    </td>
                    <td class="num"><app-rating-badge [value]="p.rating" /></td>
                  </tr>
                }
              </tbody>
            </table>
          </section>
        }
      </div>
    } @else if (data() === null) {
      <p>Match introuvable.</p>
    } @else {
      <p class="muted">Chargement des notes…</p>
    }
  `,
  styles: `
    .back { display: inline-block; margin-bottom: 1rem; color: var(--accent); text-decoration: none; }
    .match-header { margin-bottom: 1.5rem; }
    .match-header h2 { margin: 0.25rem 0; font-size: 1.6rem; }
    .match-header .score { margin: 0 0.5rem; font-variant-numeric: tabular-nums; }
    .sheets { display: grid; grid-template-columns: repeat(auto-fit, minmax(20rem, 1fr)); gap: 1.5rem; }
    .team-line { display: flex; align-items: center; gap: 0.6rem; }
    .team-line h3 { margin: 0; }
    .team-facts { margin: 0.25rem 0 0.75rem; }
    table { width: 100%; border-collapse: collapse; }
    th { text-align: left; font-size: 0.8rem; color: var(--muted); font-weight: 600; padding: 0.3rem; }
    td { padding: 0.35rem 0.3rem; border-top: 1px solid var(--border); }
    .num { text-align: right; font-variant-numeric: tabular-nums; }
    tr.sub td:first-child { padding-left: 1rem; }
    .facts { white-space: nowrap; }
    .tag { font-size: 0.7rem; padding: 0.05rem 0.3rem; margin-left: 0.25rem; border-radius: 0.3rem; background: var(--muted-bg); text-decoration: none; }
    .tag.decisive { background: #1b7f3b; color: #fff; }
    .tag.og { background: #c62f2f; color: #fff; }
    .card { display: inline-block; width: 0.6rem; height: 0.85rem; margin-left: 0.2rem; border-radius: 0.1rem; vertical-align: middle; }
    .card.yellow { background: #f2c200; }
    .card.red { background: #d62020; }
  `,
})
export class MatchDetail {
  /** Lié au paramètre de route :id. */
  readonly id = input.required({ transform: numberAttribute });

  private readonly ratingService = inject(RatingService);

  /** undefined : chargement ; null : match introuvable. */
  protected readonly data = toSignal<MatchRatings | null | undefined>(
    toObservable(this.id).pipe(
      switchMap((id) => this.ratingService.getMatchRatings(id).pipe(catchError(() => of(null)))),
    ),
    { initialValue: undefined },
  );

  protected readonly sheets = computed<TeamSheet[]>(() => {
    const d = this.data();
    if (!d) return [];
    return [
      { id: d.match.team1Id, name: d.match.team1 },
      { id: d.match.team2Id, name: d.match.team2 },
    ].map((team) => ({
      name: team.name,
      rating: d.teams.find((t) => t.teamId === team.id),
      players: d.players.filter((p) => p.teamId === team.id),
    }));
  });

  protected resultLabel(rating: TeamRating): string {
    const label = RESULT_LABELS[rating.result];
    if (rating.wonShootout === true) return `${label} (tab gagnés)`;
    if (rating.wonShootout === false) return `${label} (tab perdus)`;
    return label;
  }

  protected range(count: number): number[] {
    return Array.from({ length: count }, (_, i) => i);
  }
}
