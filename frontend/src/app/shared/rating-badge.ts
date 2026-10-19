import { Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';

/** Pastille colorée affichant une note sur 10. */
@Component({
  selector: 'app-rating-badge',
  imports: [DecimalPipe],
  template: `
    @if (value() !== null) {
      <span class="badge" [class]="level()" [title]="'Note : ' + (value() | number: '1.1-1') + ' / 10'">
        {{ value() | number: '1.1-1' }}
      </span>
    } @else {
      <span class="badge none" title="Pas de note">–</span>
    }
  `,
  styles: `
    .badge {
      display: inline-block;
      min-width: 2.6em;
      padding: 0.15em 0.4em;
      border-radius: 0.4em;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
      text-align: center;
      color: #fff;
    }
    .excellent { background: #1b7f3b; }
    .good { background: #5aa83a; }
    .average { background: #c9a227; color: #1d1d1d; }
    .poor { background: #e07b24; }
    .bad { background: #c62f2f; }
    .none { background: var(--muted-bg); color: var(--muted); }
  `,
})
export class RatingBadge {
  readonly value = input<number | null>(null);

  protected readonly level = computed(() => {
    const value = this.value() ?? 0;
    if (value >= 8) return 'badge excellent';
    if (value >= 7) return 'badge good';
    if (value >= 6) return 'badge average';
    if (value >= 5) return 'badge poor';
    return 'badge bad';
  });
}
