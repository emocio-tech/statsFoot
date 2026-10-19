import { Component, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';
import { RatingService } from '../../core/services/rating.service';
import { RatingBadge } from '../../shared/rating-badge';

@Component({
  selector: 'app-rankings',
  imports: [RatingBadge],
  template: `
    <h2>Classements</h2>
    <p class="muted">Note moyenne sur l'ensemble des matchs joués.</p>

    <div class="columns">
      <section>
        <div class="section-head">
          <h3>Joueurs</h3>
          <label>
            Matchs joués min.
            <select (change)="minMatches.set(+$any($event.target).value)">
              @for (n of [1, 3, 5, 7]; track n) {
                <option [value]="n" [selected]="n === minMatches()">{{ n }}</option>
              }
            </select>
          </label>
        </div>
        <table>
          <thead>
            <tr>
              <th scope="col" class="num">#</th>
              <th scope="col">Joueur</th>
              <th scope="col" class="num">MJ</th>
              <th scope="col" class="num">Buts</th>
              <th scope="col" class="num">Min</th>
              <th scope="col" class="num">Moy.</th>
            </tr>
          </thead>
          <tbody>
            @for (p of players(); track p.playerId; let i = $index) {
              <tr>
                <td class="num muted">{{ i + 1 }}</td>
                <td>{{ p.name }} <span class="muted">· {{ p.team }}</span></td>
                <td class="num">{{ p.matches }}</td>
                <td class="num">{{ p.goals }}</td>
                <td class="num">{{ p.minutesPlayed }}</td>
                <td class="num"><app-rating-badge [value]="p.averageRating" /></td>
              </tr>
            } @empty {
              <tr><td colspan="6" class="muted">Chargement…</td></tr>
            }
          </tbody>
        </table>
      </section>

      <section>
        <div class="section-head"><h3>Équipes</h3></div>
        <table>
          <thead>
            <tr>
              <th scope="col" class="num">#</th>
              <th scope="col">Équipe</th>
              <th scope="col" class="num">MJ</th>
              <th scope="col" class="num">Pts</th>
              <th scope="col" class="num">Buts</th>
              <th scope="col" class="num" title="Points de fair-play (barème FIFA)">FP</th>
              <th scope="col" class="num">Moy.</th>
            </tr>
          </thead>
          <tbody>
            @for (t of teams(); track t.teamId; let i = $index) {
              <tr>
                <td class="num muted">{{ i + 1 }}</td>
                <td>{{ t.team }}</td>
                <td class="num">{{ t.matches }}</td>
                <td class="num">{{ t.points }}</td>
                <td class="num">{{ t.goalsFor }}–{{ t.goalsAgainst }}</td>
                <td class="num">{{ t.fairPlayPoints }}</td>
                <td class="num"><app-rating-badge [value]="t.averageRating" /></td>
              </tr>
            } @empty {
              <tr><td colspan="7" class="muted">Chargement…</td></tr>
            }
          </tbody>
        </table>
      </section>
    </div>
  `,
  styles: `
    .columns { display: grid; grid-template-columns: repeat(auto-fit, minmax(22rem, 1fr)); gap: 2rem; align-items: start; }
    .section-head { display: flex; align-items: center; justify-content: space-between; gap: 1rem; }
    .section-head label { font-size: 0.85rem; color: var(--muted); }
    select { margin-left: 0.3rem; font: inherit; }
    table { width: 100%; border-collapse: collapse; }
    th { text-align: left; font-size: 0.8rem; color: var(--muted); font-weight: 600; padding: 0.3rem; }
    td { padding: 0.35rem 0.3rem; border-top: 1px solid var(--border); }
    .num { text-align: right; font-variant-numeric: tabular-nums; }
  `,
})
export class Rankings {
  private readonly ratingService = inject(RatingService);

  protected readonly minMatches = signal(3);

  protected readonly players = toSignal(
    toObservable(this.minMatches).pipe(switchMap((min) => this.ratingService.getPlayerRanking(min, 30))),
    { initialValue: [] },
  );

  protected readonly teams = toSignal(this.ratingService.getTeamRanking(), { initialValue: [] });
}
