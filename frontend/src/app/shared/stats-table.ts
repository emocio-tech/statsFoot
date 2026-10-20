import { Component, computed, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RatingBadge } from './rating-badge';

export type StatsColumnKind = 'text' | 'int' | 'km' | 'rating' | 'motm';

export interface StatsColumn<T> {
  key: keyof T & string;
  label: string;
  /** Libellé complet, affiché au survol de l'en-tête. */
  title?: string;
  kind: StatsColumnKind;
  /** Texte secondaire affiché après la valeur (colonne texte). */
  sub?: keyof T & string;
}

/**
 * Tableau de statistiques triable par colonne.
 * Une valeur null (donnée non importée) s'affiche « – » et reste en fin de tri.
 */
@Component({
  selector: 'app-stats-table',
  imports: [DecimalPipe, RatingBadge],
  template: `
    <div class="scroll">
      <table>
        <thead>
          <tr>
            <th scope="col" class="num">#</th>
            @for (c of columns(); track c.key) {
              <th scope="col" [class.num]="c.kind !== 'text'" [class.sticky]="$first"
                  [attr.aria-sort]="sortKey() === c.key ? (descending() ? 'descending' : 'ascending') : null">
                <button type="button" [title]="c.title ?? c.label" (click)="sortBy(c)">
                  {{ c.label }}
                  @if (sortKey() === c.key) {
                    <span aria-hidden="true">{{ descending() ? '▾' : '▴' }}</span>
                  }
                </button>
              </th>
            }
          </tr>
        </thead>
        <tbody>
          @for (row of sorted(); track row[rowKey()]; let i = $index) {
            <tr>
              <td class="num muted">{{ i + 1 }}</td>
              @for (c of columns(); track c.key) {
                @let v = $any(row)[c.key];
                @switch (c.kind) {
                  @case ('text') {
                    <td [class.sticky]="$first">
                      {{ v }}
                      @if (c.sub) {
                        <span class="muted">· {{ $any(row)[c.sub] }}</span>
                      }
                    </td>
                  }
                  @case ('rating') {
                    <td class="num"><app-rating-badge [value]="v" /></td>
                  }
                  @case ('motm') {
                    <td class="num" [title]="v === null ? 'Donnée non disponible' : 'Homme du match'">
                      @if (v === null) {
                        <span class="muted">–</span>
                      } @else if (v === 0) {
                        non
                      } @else {
                        <strong>oui</strong>
                        @if (v > 1) { ({{ v }}) }
                      }
                    </td>
                  }
                  @default {
                    <td class="num" [class.muted]="v === null" [title]="v === null ? 'Donnée non disponible' : ''">
                      @if (v === null) {
                        –
                      } @else if (c.kind === 'km') {
                        {{ v | number: '1.1-1' }}
                      } @else {
                        {{ v }}
                      }
                    </td>
                  }
                }
              }
            </tr>
          } @empty {
            <tr><td [attr.colspan]="columns().length + 1" class="muted">{{ emptyText() }}</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: `
    .scroll { overflow-x: auto; border: 1px solid var(--border); border-radius: 0.4rem; background: var(--surface); }
    table { width: 100%; border-collapse: collapse; white-space: nowrap; }
    th { font-size: 0.8rem; color: var(--muted); font-weight: 600; padding: 0; text-align: left; }
    th button {
      font: inherit; color: inherit; background: none; border: 0; cursor: pointer;
      padding: 0.45rem 0.4rem; width: 100%; text-align: inherit;
    }
    th button:hover { color: var(--text); }
    td { padding: 0.35rem 0.4rem; border-top: 1px solid var(--border); }
    tbody tr:hover td { background: var(--hover); }
    .num { text-align: right; font-variant-numeric: tabular-nums; }
    .sticky { position: sticky; left: 0; background: var(--surface); z-index: 1; }
  `,
})
export class StatsTable<T extends object> {
  readonly rows = input.required<T[]>();
  readonly columns = input.required<StatsColumn<T>[]>();
  readonly rowKey = input.required<keyof T & string>();
  readonly emptyText = input('Aucune donnée.');

  protected readonly sortKey = signal<(keyof T & string) | null>(null);
  protected readonly descending = signal(true);

  protected readonly sorted = computed(() => {
    const rows = this.rows();
    const key = this.sortKey();
    if (key === null) return rows;
    const direction = this.descending() ? -1 : 1;
    return [...rows].sort((a, b) => {
      const x = a[key];
      const y = b[key];
      if (x === y) return 0;
      if (x === null || x === undefined) return 1;
      if (y === null || y === undefined) return -1;
      if (typeof x === 'string' && typeof y === 'string') return direction * x.localeCompare(y, 'fr');
      return direction * (Number(x) - Number(y));
    });
  });

  protected sortBy(column: StatsColumn<T>): void {
    if (this.sortKey() === column.key) {
      this.descending.update((d) => !d);
    } else {
      this.sortKey.set(column.key);
      // nombres du plus grand au plus petit, noms de A à Z
      this.descending.set(column.kind !== 'text');
    }
  }
}
