import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Observable, Subscription, catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { normalizeName } from '../../player/player-comparison-page/player-comparison-page';
import { GENERIC_ERROR } from '../../player/player-picker/player-picker';
import { ComparisonLine } from '../../player/player.models';
import { TeamApiService } from '../team-api.service';
import { TeamComparison, TeamSearchResult, TeamSummary } from '../team.models';

interface FieldState {
  text: string;
  chosen: TeamSummary | null;
  candidates: TeamSummary[];
  error: string | null;
}

type Outcome = { result: TeamSearchResult } | { error: string } | null;
type FieldSignal = ReturnType<typeof signal<FieldState>>;

const EMPTY_FIELD: FieldState = { text: '', chosen: null, candidates: [], error: null };
const BLANK_ERROR = 'Please enter a team name.';
const NOT_FOUND_ERROR = 'Team not found.';

let nextPageId = 0;

@Component({
  selector: 'app-team-comparison-page',
  templateUrl: './team-comparison-page.html',
  styleUrl: './team-comparison-page.css',
})
export class TeamComparisonPage {
  private readonly api = inject(TeamApiService);
  private request?: Subscription;

  readonly idA = `team-a-name-${nextPageId}`;
  readonly idB = `team-b-name-${nextPageId++}`;

  readonly fieldA = signal<FieldState>({ ...EMPTY_FIELD });
  readonly fieldB = signal<FieldState>({ ...EMPTY_FIELD });
  readonly report = signal<TeamComparison | null>(null);
  readonly message = signal<string | null>(null);

  editA(text: string): void {
    this.edit(this.fieldA, text);
  }

  editB(text: string): void {
    this.edit(this.fieldB, text);
  }

  chooseA(team: TeamSummary): void {
    this.choose(this.fieldA, team);
  }

  chooseB(team: TeamSummary): void {
    this.choose(this.fieldB, team);
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

  private apply(field: FieldSignal, outcome: Outcome): void {
    if (outcome === null) {
      return;
    }
    if ('error' in outcome) {
      field.update((f) => ({ ...f, error: outcome.error }));
      return;
    }
    const { teams } = outcome.result;
    if (teams.length === 0) {
      field.update((f) => ({ ...f, error: NOT_FOUND_ERROR }));
    } else if (teams.length === 1) {
      field.update((f) => ({ ...f, chosen: teams[0] }));
    } else {
      const typed = normalizeName(field().text);
      const exact = teams.filter((t) => normalizeName(t.name) === typed);
      if (exact.length === 1) {
        field.update((f) => ({ ...f, chosen: exact[0] }));
        return;
      }
      field.update((f) => ({ ...f, candidates: teams }));
    }
  }

  private edit(field: FieldSignal, text: string): void {
    this.request?.unsubscribe();
    this.report.set(null);
    this.message.set(null);
    field.set({ text, chosen: null, candidates: [], error: null });
  }

  private choose(field: FieldSignal, team: TeamSummary): void {
    field.set({ text: team.name, chosen: team, candidates: [], error: null });
  }

  private errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && typeof error.error?.error === 'string') {
      return error.error.error;
    }
    return GENERIC_ERROR;
  }
}
