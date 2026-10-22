import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Observable, Subscription, catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { PlayerApiService } from '../player-api.service';
import {
  ComparisonLine,
  PlayerComparison,
  PlayerRole,
  PlayerSearchResult,
  PlayerSummary,
} from '../player.models';
import { GENERIC_ERROR } from '../player-picker/player-picker';

interface FieldState {
  text: string;
  chosen: PlayerSummary | null;
  candidates: PlayerSummary[];
  error: string | null;
}

type Outcome = { result: PlayerSearchResult } | { error: string } | null;

const EMPTY_FIELD: FieldState = { text: '', chosen: null, candidates: [], error: null };

let nextPageId = 0;

@Component({
  selector: 'app-player-comparison-page',
  templateUrl: './player-comparison-page.html',
  styleUrl: './player-comparison-page.css',
})
export class PlayerComparisonPage {
  private readonly api = inject(PlayerApiService);
  private request?: Subscription;

  readonly idA = `player-a-name-${nextPageId}`;
  readonly idB = `player-b-name-${nextPageId++}`;

  readonly fieldA = signal<FieldState>({ ...EMPTY_FIELD });
  readonly fieldB = signal<FieldState>({ ...EMPTY_FIELD });
  readonly report = signal<PlayerComparison | null>(null);
  readonly message = signal<string | null>(null);

  editA(text: string): void {
    this.edit(this.fieldA, text);
  }

  editB(text: string): void {
    this.edit(this.fieldB, text);
  }

  chooseA(player: PlayerSummary): void {
    this.choose(this.fieldA, player);
  }

  chooseB(player: PlayerSummary): void {
    this.choose(this.fieldB, player);
  }

  compare(): void {
    this.request?.unsubscribe();
    this.report.set(null);
    this.message.set(null);
    this.fieldA.update((f) => ({ ...f, candidates: [], error: null }));
    this.fieldB.update((f) => ({ ...f, candidates: [], error: null }));

    const a = this.fieldA();
    const b = this.fieldB();
    const blankA = a.text.trim() === '';
    const blankB = b.text.trim() === '';
    if (blankA) {
      this.fieldA.update((f) => ({ ...f, error: BLANK_ERROR }));
    }
    if (blankB) {
      this.fieldB.update((f) => ({ ...f, error: BLANK_ERROR }));
    }

    this.request = forkJoin([
      blankA ? of<Outcome>(null) : this.resolve(a),
      blankB ? of<Outcome>(null) : this.resolve(b),
    ])
      .pipe(
        switchMap(([outcomeA, outcomeB]) => {
          this.apply(this.fieldA, outcomeA);
          this.apply(this.fieldB, outcomeB);
          const chosenA = this.fieldA().chosen;
          const chosenB = this.fieldB().chosen;
          if (chosenA === null || chosenB === null) {
            return of(null);
          }
          return this.api.compare(chosenA.id, chosenB.id);
        }),
      )
      .subscribe({
        next: (report) => this.report.set(report),
        error: (error) => this.message.set(this.errorMessage(error)),
      });
  }

  roleLabel(role: PlayerRole): string {
    return role === 'GOALKEEPER' ? 'Goalkeeper' : 'Outfield player';
  }

  hasBar(line: ComparisonLine): boolean {
    return line.available && line.leader !== null;
  }

  gapPercent(line: ComparisonLine): number {
    const a = line.valueA;
    const b = line.valueB;
    if (a === null || b === null) {
      return 0;
    }
    const max = Math.max(Math.abs(a), Math.abs(b));
    if (max === 0) {
      return 0;
    }
    return Math.round((Math.abs(a - b) / max) * 10000) / 100;
  }

  differenceText(line: ComparisonLine): string | null {
    if (line.difference === null) {
      return null;
    }
    return line.difference > 0 ? `+${line.difference}` : `${line.difference}`;
  }

  private resolve(field: FieldState): Observable<Outcome> {
    if (field.chosen !== null) {
      return of<Outcome>(null);
    }
    return this.api.search(field.text).pipe(
      map((result): Outcome => ({ result })),
      catchError((error) => of<Outcome>({ error: this.errorMessage(error) })),
    );
  }

  private apply(field: ReturnType<typeof signal<FieldState>>, outcome: Outcome): void {
    if (outcome === null) {
      return;
    }
    if ('error' in outcome) {
      field.update((f) => ({ ...f, error: outcome.error }));
      return;
    }
    const { result } = outcome;
    if (result.total === 0 || result.players.length === 0) {
      field.update((f) => ({ ...f, error: 'Player not found.' }));
    } else if (result.total === 1) {
      field.update((f) => ({ ...f, chosen: result.players[0] }));
    } else {
      const typed = normalizeName(field().text);
      const exact = result.players.filter((p) => normalizeName(p.name) === typed);
      if (exact.length === 1) {
        field.update((f) => ({ ...f, chosen: exact[0] }));
        return;
      }
      field.update((f) => ({
        ...f,
        candidates: result.players,
        error: result.truncated ? TRUNCATED_MESSAGE : null,
      }));
    }
  }

  private edit(field: ReturnType<typeof signal<FieldState>>, text: string): void {
    this.request?.unsubscribe();
    this.report.set(null);
    this.message.set(null);
    field.set({ text, chosen: null, candidates: [], error: null });
  }

  private choose(field: ReturnType<typeof signal<FieldState>>, player: PlayerSummary): void {
    field.set({ text: player.name, chosen: player, candidates: [], error: null });
  }

  private errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && typeof error.error?.error === 'string') {
      return error.error.error;
    }
    return GENERIC_ERROR;
  }
}

function normalizeName(name: string): string {
  return name
    .trim()
    .replace(/\s+/g, ' ')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '');
}

const BLANK_ERROR ='Please enter a player name.';
const TRUNCATED_MESSAGE = 'More than 50 players match: please refine your search.';
