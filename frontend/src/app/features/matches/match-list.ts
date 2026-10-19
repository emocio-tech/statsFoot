import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { RatingService } from '../../core/services/rating.service';
import { MatchSummary, Stage } from '../../core/models/rating';
import { RatingBadge } from '../../shared/rating-badge';
import { STAGE_ORDER, StageLabelPipe } from '../../shared/stage-label.pipe';

interface MatchSection {
  title: string;
  matches: MatchSummary[];
}

@Component({
  selector: 'app-match-list',
  imports: [DatePipe, RouterLink, RatingBadge, StageLabelPipe],
  template: `
    <h2>Matchs</h2>

    <nav class="filters" aria-label="Filtrer par phase">
      <button type="button" [class.active]="stage() === null" (click)="stage.set(null)">Tous</button>
      @for (s of stages; track s) {
        <button type="button" [class.active]="stage() === s" (click)="stage.set(s)">{{ s | stageLabel }}</button>
      }
    </nav>

    @if (matches() === undefined) {
      <p class="muted">Chargement des matchs…</p>
    } @else {
      @for (section of sections(); track section.title) {
        <section>
          <h3>{{ section.title }}</h3>
          <ul class="matches">
            @for (m of section.matches; track m.id) {
              <li>
                <a [routerLink]="['/matchs', m.id]">
                  <span class="date">{{ m.kickoffAt | date: 'd MMM, HH:mm' }}</span>
                  <span class="team home">{{ m.team1 }} <app-rating-badge [value]="m.rating1" /></span>
                  <span class="score">
                    {{ m.score1 }} – {{ m.score2 }}
                    @if (m.penalties1 !== null) {
                      <small>(tab {{ m.penalties1 }}–{{ m.penalties2 }})</small>
                    } @else if (m.extraTime) {
                      <small>(a.p.)</small>
                    }
                  </span>
                  <span class="team away"><app-rating-badge [value]="m.rating2" /> {{ m.team2 }}</span>
                </a>
              </li>
            }
          </ul>
        </section>
      } @empty {
        <p class="muted">Aucun match.</p>
      }
    }
  `,
  styles: `
    .filters { display: flex; flex-wrap: wrap; gap: 0.4rem; margin-bottom: 1rem; }
    .filters button {
      border: 1px solid var(--border); background: var(--surface); color: inherit;
      padding: 0.3rem 0.7rem; border-radius: 999px; cursor: pointer; font: inherit;
    }
    .filters button.active { background: var(--accent); border-color: var(--accent); color: #fff; }
    h3 { margin: 1.5rem 0 0.5rem; font-size: 1rem; color: var(--muted); }
    .matches { list-style: none; margin: 0; padding: 0; }
    .matches a {
      display: grid;
      grid-template-columns: 7.5rem 1fr 6.5rem 1fr;
      align-items: center;
      gap: 0.75rem;
      padding: 0.55rem 0.75rem;
      border-bottom: 1px solid var(--border);
      color: inherit;
      text-decoration: none;
    }
    .matches a:hover { background: var(--hover); }
    .date { color: var(--muted); font-size: 0.85rem; }
    .home { text-align: right; }
    .score { text-align: center; font-weight: 700; font-variant-numeric: tabular-nums; }
    .score small { display: block; font-weight: 400; color: var(--muted); }
    @media (max-width: 600px) {
      .matches a { grid-template-columns: 1fr 5rem 1fr; }
      .date { grid-column: 1 / -1; }
    }
  `,
})
export class MatchList {
  protected readonly stages = STAGE_ORDER;
  protected readonly stage = signal<Stage | null>(null);
  protected readonly matches = toSignal(inject(RatingService).getMatches());

  private readonly stageLabel = new StageLabelPipe();

  /** Matchs regroupés par groupe (phase de groupes) ou par tour. */
  protected readonly sections = computed<MatchSection[]>(() => {
    const selected = this.stage();
    const bySection = new Map<string, MatchSummary[]>();
    const sorted = [...(this.matches() ?? [])]
      .filter((m) => selected === null || m.stage === selected)
      .sort(
        (a, b) =>
          STAGE_ORDER.indexOf(a.stage) - STAGE_ORDER.indexOf(b.stage) ||
          (a.groupName ?? '').localeCompare(b.groupName ?? '') ||
          a.kickoffAt.localeCompare(b.kickoffAt),
      );
    for (const match of sorted) {
      const title = this.stageLabel.transform(match.stage, match.groupName);
      bySection.set(title, [...(bySection.get(title) ?? []), match]);
    }
    return [...bySection].map(([title, matches]) => ({ title, matches }));
  });
}
