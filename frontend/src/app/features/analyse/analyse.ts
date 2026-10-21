import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { of, startWith, switchMap } from 'rxjs';
import { AnalyseService } from '../../core/services/analyse.service';
import { AnalyseAnswer, RankedEntry } from '../../core/models/analyse';
import { StatsColumn, StatsTable } from '../../shared/stats-table';
import { PlayerReport } from '../reports/player-report';
import { TeamReport } from '../reports/team-report';

/** Suggestion cliquée ou question saisie. */
type Request = { questionId: string } | { text: string };

/** Ligne du top 5 telle qu'affichée par le tableau partagé. */
interface TopRow {
  key: string;
  rank: number;
  name: string;
  team: string;
  value: string;
}

@Component({
  selector: 'app-analyse',
  imports: [StatsTable, PlayerReport, TeamReport],
  template: `
    <h2>Analyse</h2>
    <p class="muted">
      Posez une question ou choisissez-en une : la réponse, le critère appliqué et le classement des cinq premiers.
    </p>

    <form class="search" role="search" (submit)="$event.preventDefault(); ask(query.value)">
      <input #query type="search" placeholder="Ex. : qui a pris le plus de jaunes ?" aria-label="Votre question" />
      <button type="submit">Rechercher</button>
    </form>
    @if (assistant().enabled) {
      <p class="notice muted">Une question non reconnue peut être transmise à Anthropic (Claude) pour être interprétée.</p>
    }

    <nav class="suggestions" aria-label="Questions proposées">
      @for (q of questions(); track q.id) {
        <button type="button" [class.active]="activeId() === q.id" (click)="choose(q.id)">{{ q.label }}</button>
      }
    </nav>

    @if (request() !== null) {
      @let a = answer();
      @if (a === undefined) {
        <p class="muted">Chargement…</p>
      } @else if (a !== null) {
        <section class="answer" aria-live="polite">
          @if (a.status === 'AMBIGUOUS') {
            <p>
              Votre question peut correspondre à plusieurs questions. Précisez :
              @if (a.interpretation?.source === 'ASSISTANT') { <span class="muted">(proposées par l'assistant)</span> }
            </p>
            <nav class="suggestions">
              @for (c of a.candidates; track c.id) {
                <button type="button" (click)="choose(c.id)">{{ c.label }}</button>
              }
            </nav>
          } @else if (a.status === 'NOT_RECOGNIZED') {
            <p>Question non reconnue. Questions disponibles :</p>
            <nav class="suggestions">
              @for (c of a.candidates; track c.id) {
                <button type="button" (click)="choose(c.id)">{{ c.label }}</button>
              }
            </nav>
          } @else if (a.status === 'NO_DATA') {
            <h3>{{ a.question?.label }}</h3>
            <p class="muted">Aucune donnée importée pour répondre à cette question.</p>
          } @else {
            <p class="question">{{ a.question?.label }}</p>
            @if (a.interpretation; as i) {
              <p class="understood muted">
                Question comprise comme : {{ i.understoodAs }} ({{ i.source === 'ASSISTANT' ? 'assistant' : 'mots-clés' }})
              </p>
            }
            <h3>{{ headline(a) }}</h3>
            <p class="criterion">
              Critère : {{ a.explanation }}
              @if (a.fictitious) {
                <span class="fictitious">données fictives</span>
              }
            </p>
            @if (a.winners.length > 1) {
              <p class="muted">{{ a.winners.length }} ex aequo, à égalité après la règle de départage.</p>
            }
            @if (a.winners[0]; as w) {
              <section class="report">
                @if (w.scope === 'PLAYER') {
                  <app-player-report [id]="w.id" />
                } @else {
                  <app-team-report [id]="w.id" />
                }
              </section>
            }
            <h4>Top 5</h4>
            <app-stats-table [rows]="topRows()" [columns]="topColumns" rowKey="key" [showIndex]="false" />
          }
        </section>
      }
    }
  `,
  styles: `
    .search { display: flex; gap: 0.4rem; margin: 1rem 0 0.5rem; max-width: 36rem; }
    .search input { flex: 1; min-width: 0; font: inherit; padding: 0.4rem 0.6rem; border: 1px solid var(--border); border-radius: 0.4rem; background: var(--surface); color: var(--text); }
    .search button { font: inherit; padding: 0.4rem 0.9rem; border: 0; border-radius: 0.4rem; background: var(--accent); color: #fff; cursor: pointer; }
    .notice { font-size: 0.85rem; margin: 0 0 0.5rem; }
    .understood { margin: 0.1rem 0 0; font-size: 0.85rem; }
    .suggestions { display: flex; flex-wrap: wrap; gap: 0.4rem; margin: 1rem 0; }
    .suggestions button {
      font: inherit; padding: 0.35rem 0.8rem; border-radius: 999px; cursor: pointer;
      border: 1px solid var(--border); background: var(--surface); color: var(--text);
    }
    .suggestions button.active { background: var(--accent); border-color: var(--accent); color: #fff; }
    .answer { margin-top: 1.5rem; }
    .question { margin: 0; color: var(--muted); font-size: 0.9rem; }
    h3 { margin: 0.2rem 0 0.4rem; font-size: 1.5rem; }
    .criterion { margin: 0 0 0.5rem; }
    .fictitious {
      display: inline-block; margin-left: 0.4rem; padding: 0.05rem 0.5rem; border-radius: 999px;
      border: 1px solid var(--accent); color: var(--accent); font-size: 0.8rem;
    }
    h4 { margin: 1.2rem 0 0.4rem; }
    .report { margin-top: 1rem; padding: 1rem; border: 1px solid var(--border); border-radius: 0.5rem; background: var(--surface); }
  `,
})
export class Analyse {
  private readonly analyseService = inject(AnalyseService);

  protected readonly questions = toSignal(this.analyseService.getQuestions(), { initialValue: [] });
  protected readonly assistant = toSignal(this.analyseService.getAssistantStatus(), { initialValue: { enabled: false } });
  protected readonly request = signal<Request | null>(null);

  /** undefined pendant le chargement, null sans question posée. */
  protected readonly answer = toSignal(
    toObservable(this.request).pipe(
      switchMap((r) => {
        if (r === null) return of(null);
        const call = 'questionId' in r ? this.analyseService.answer(r.questionId) : this.analyseService.search(r.text);
        return call.pipe(startWith(undefined));
      }),
    ),
    { initialValue: null },
  );

  /** Suggestion mise en évidence : celle cliquée, ou celle reconnue dans la question saisie. */
  protected readonly activeId = computed(() => {
    const r = this.request();
    if (r !== null && 'questionId' in r) return r.questionId;
    return this.answer()?.question?.id ?? null;
  });

  protected choose(questionId: string): void {
    this.request.set({ questionId });
  }

  protected ask(text: string): void {
    if (text.trim() !== '') this.request.set({ text });
  }

  protected readonly topRows = computed<TopRow[]>(() =>
    (this.answer()?.top ?? []).map((e) => ({
      key: `${e.scope}-${e.id}`,
      rank: e.rank,
      name: e.name,
      team: e.team ?? '',
      value: formatValue(e),
    })),
  );

  protected readonly topColumns: StatsColumn<TopRow>[] = [
    { key: 'rank', label: 'Rang', kind: 'int' },
    { key: 'name', label: 'Nom', kind: 'text', sub: 'team' },
    { key: 'value', label: 'Valeur', kind: 'text' },
  ];

  protected headline(answer: AnalyseAnswer): string {
    const names = answer.winners.map((w) => (w.team ? `${w.name} (${w.team})` : w.name)).join(' et ');
    const first = answer.winners[0];
    return first ? `${names}, ${formatValue(first)}` : '';
  }
}

function formatValue(entry: RankedEntry): string {
  if (entry.valueLabel) return entry.valueLabel;
  if (entry.value === null) return '–';
  return `${entry.value.toLocaleString('fr-FR')} ${entry.unit}`;
}
