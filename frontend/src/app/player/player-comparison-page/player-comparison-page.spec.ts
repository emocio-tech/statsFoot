import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PlayerComparisonPage } from './player-comparison-page';
import { ComparisonLine, PlayerComparison, PlayerSummary } from '../player.models';

const RANGEL: PlayerSummary = { id: 1, name: 'Raul RANGEL', teamCode: 'MEX', teamName: 'Mexico' };
const RANGEL2: PlayerSummary = { id: 3, name: 'Raul RANGEL2', teamCode: 'CHI', teamName: 'Chile' };
const SUZUKI: PlayerSummary = { id: 2, name: 'Zion SUZUKI', teamCode: 'JPN', teamName: 'Japan' };

function line(overrides: Partial<ComparisonLine>): ComparisonLine {
  return {
    key: 'goals',
    label: 'Goals',
    valueA: 5,
    valueB: 3,
    difference: 2,
    leader: 'A',
    available: true,
    ...overrides,
  };
}

function report(lines: ComparisonLine[], overrides: Partial<PlayerComparison> = {}): PlayerComparison {
  return {
    playerA: { id: 1, name: 'Raul RANGEL', teamName: 'Mexico', teamCode: 'MEX', role: 'GOALKEEPER' },
    playerB: { id: 2, name: 'Zion SUZUKI', teamName: 'Japan', teamCode: 'JPN', role: 'GOALKEEPER' },
    lines,
    summary: { leadsA: 3, leadsB: 1, ties: 2 },
    roleWarning: null,
    verdict: 'Raul RANGEL leads on more statistics than Zion SUZUKI.',
    ...overrides,
  };
}

describe('PlayerComparisonPage', () => {
  let fixture: ComponentFixture<PlayerComparisonPage>;
  let el: HTMLElement;
  let http: HttpTestingController;

  const q = (id: string) => el.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const qa = (id: string) => el.querySelectorAll(`[data-testid="${id}"]`);
  const pageMessage = () => el.querySelector('main > [data-testid="message"]') as HTMLElement | null;
  const compareButton = () => q('compare-button') as HTMLButtonElement;
  const input = (side: 'a' | 'b') => q(`name-${side}`) as HTMLInputElement;
  const searches = () => http.match((r) => r.url === '/api/players');
  const searchFor = (name: string) =>
    http.expectOne((r) => r.url === '/api/players' && r.params.get('name') === name);
  const compareRequests = () => http.match((r) => r.url === '/api/players/compare');

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function type(side: 'a' | 'b', text: string): void {
    input(side).value = text;
    input(side).dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function one(player: PlayerSummary) {
    return { total: 1, truncated: false, players: [player] };
  }

  async function compareWith(result: PlayerComparison): Promise<void> {
    type('a', RANGEL.name);
    type('b', SUZUKI.name);
    compareButton().click();
    searchFor(RANGEL.name).flush(one(RANGEL));
    searchFor(SUZUKI.name).flush(one(SUZUKI));
    http.expectOne((r) => r.url === '/api/players/compare').flush(result);
    await settle();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PlayerComparisonPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PlayerComparisonPage);
    el = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('twoFieldsAndOneButton_noPerFieldSearchButton', () => {
    expect(el.querySelectorAll('input').length).toBe(2);
    expect(el.querySelectorAll('button').length).toBe(1);
    expect(compareButton().disabled).toBe(false);
    expect(q('search-button')).toBeNull();
    const ids = Array.from(el.querySelectorAll('input')).map((i) => i.id);
    expect(ids.every((id) => id !== '')).toBe(true);
    expect(new Set(ids).size).toBe(2);
    expect(el.querySelector(`label[for="${input('a').id}"]`)?.textContent?.trim()).toBe('Player A');
    expect(el.querySelector(`label[for="${input('b').id}"]`)?.textContent?.trim()).toBe('Player B');
  });

  it('enterKey_submitsTheComparison', () => {
    type('a', 'rangel');
    type('b', 'suzuki');
    const form = el.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit', { cancelable: true }));
    searchFor('rangel').flush(one(RANGEL));
    searchFor('suzuki').flush(one(SUZUKI));
    const req = http.expectOne((r) => r.url === '/api/players/compare');
    expect(req.request.params.get('playerA')).toBe('1');
    expect(req.request.params.get('playerB')).toBe('2');
    req.flush(report([line({})]));
  });

  it('notFoundError_setsAriaInvalidAndDescribedBy', async () => {
    type('a', 'nobody');
    type('b', 'suzuki');
    compareButton().click();
    searchFor('nobody').flush({ total: 0, truncated: false, players: [] });
    searchFor('suzuki').flush(one(SUZUKI));
    await settle();
    const error = q('error-a') as HTMLElement;
    expect(error.getAttribute('role')).toBe('alert');
    expect(error.id).not.toBe('');
    expect(input('a').getAttribute('aria-invalid')).toBe('true');
    expect(input('a').getAttribute('aria-describedby')).toBe(error.id);
    expect(input('b').getAttribute('aria-invalid')).toBeNull();
    expect(input('b').getAttribute('aria-describedby')).toBeNull();
  });

  it('compare_resolvesBothNamesThenShowsReport', async () => {
    type('a', 'rangel');
    type('b', 'suzuki');
    compareButton().click();
    searchFor('rangel').flush(one(RANGEL));
    searchFor('suzuki').flush(one(SUZUKI));
    const req = http.expectOne((r) => r.url === '/api/players/compare');
    expect(req.request.params.get('playerA')).toBe('1');
    expect(req.request.params.get('playerB')).toBe('2');
    req.flush(report([line({})]));
    await settle();
    expect(q('comparison')).not.toBeNull();
    expect(q('identity-a')?.textContent).toContain('Raul RANGEL');
    expect(q('identity-b')?.textContent).toContain('Zion SUZUKI');
  });

  it('blankField_showsErrorAndSendsNoRequest', async () => {
    compareButton().click();
    await settle();
    expect(q('error-a')?.textContent?.trim()).toBe('Please enter a player name.');
    expect(q('error-b')?.textContent?.trim()).toBe('Please enter a player name.');
    expect(q('comparison')).toBeNull();
    expect(searches().length).toBe(0);
    expect(compareRequests().length).toBe(0);

    type('a', '   ');
    type('b', 'suzuki');
    compareButton().click();
    const pending = searches();
    expect(pending.length).toBe(1);
    pending[0].flush(one(SUZUKI));
    await settle();
    expect(q('error-a')?.textContent?.trim()).toBe('Please enter a player name.');
    expect(q('error-b')).toBeNull();
    expect(compareRequests().length).toBe(0);
  });

  it('unknownName_showsPlayerNotFoundForThatField', async () => {
    type('a', 'nobody');
    type('b', 'suzuki');
    compareButton().click();
    searchFor('nobody').flush({ total: 0, truncated: false, players: [] });
    searchFor('suzuki').flush(one(SUZUKI));
    await settle();
    expect(q('error-a')?.textContent?.trim()).toBe('Player not found.');
    expect(q('error-b')).toBeNull();
    expect(q('comparison')).toBeNull();
    expect(compareRequests().length).toBe(0);
  });

  it('severalMatches_listsCandidatesUnderField', async () => {
    type('a', 'raul');
    type('b', 'suzuki');
    compareButton().click();
    searchFor('raul').flush({ total: 2, truncated: false, players: [RANGEL, RANGEL2] });
    searchFor('suzuki').flush(one(SUZUKI));
    await settle();
    const rows = (q('candidates-a') as HTMLElement).querySelectorAll('[data-testid="candidate-row"]');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent?.trim()).toBe('Raul RANGEL (Mexico)');
    expect(rows[1].textContent?.trim()).toBe('Raul RANGEL2 (Chile)');
    expect(q('candidates-b')).toBeNull();
    expect(q('comparison')).toBeNull();
    expect(compareRequests().length).toBe(0);
  });

  it('exactNameAmongSeveralMatches_usesExactPlayerWithoutList', async () => {
    type('a', 'Raul RANGEL');
    type('b', 'suzuki');
    compareButton().click();
    searchFor('Raul RANGEL').flush({ total: 2, truncated: false, players: [RANGEL, RANGEL2] });
    searchFor('suzuki').flush(one(SUZUKI));
    const req = http.expectOne((r) => r.url === '/api/players/compare');
    expect(req.request.params.get('playerA')).toBe('1');
    expect(req.request.params.get('playerB')).toBe('2');
    req.flush(report([line({})]));
    await settle();
    expect(q('candidates-a')).toBeNull();
    expect(q('comparison')).not.toBeNull();
  });

  it('exactMatchIgnoresCaseAccentsAndSpaces', async () => {
    type('a', '  RAÚL   rangel ');
    type('b', 'suzuki');
    compareButton().click();
    searchFor('  RAÚL   rangel ').flush({ total: 2, truncated: false, players: [RANGEL2, RANGEL] });
    searchFor('suzuki').flush(one(SUZUKI));
    const req = http.expectOne((r) => r.url === '/api/players/compare');
    expect(req.request.params.get('playerA')).toBe('1');
    req.flush(report([line({})]));
    await settle();
    expect(q('candidates-a')).toBeNull();
    expect(q('comparison')).not.toBeNull();
  });

  it('severalExactNames_showsCandidateList', async () => {
    const twin: PlayerSummary = { id: 4, name: 'raul rangel', teamCode: 'CHI', teamName: 'Chile' };
    type('a', 'Raul RANGEL');
    type('b', 'suzuki');
    compareButton().click();
    searchFor('Raul RANGEL').flush({ total: 3, truncated: false, players: [RANGEL, twin, RANGEL2] });
    searchFor('suzuki').flush(one(SUZUKI));
    await settle();
    const rows = (q('candidates-a') as HTMLElement).querySelectorAll('[data-testid="candidate-row"]');
    expect(rows.length).toBe(3);
    expect(q('comparison')).toBeNull();
    expect(compareRequests().length).toBe(0);
  });

  it('noExactName_showsCandidateList', async () => {
    type('a', 'Raul');
    type('b', 'suzuki');
    compareButton().click();
    searchFor('Raul').flush({ total: 2, truncated: false, players: [RANGEL, RANGEL2] });
    searchFor('suzuki').flush(one(SUZUKI));
    await settle();
    const rows = (q('candidates-a') as HTMLElement).querySelectorAll('[data-testid="candidate-row"]');
    expect(rows.length).toBe(2);
    expect(q('comparison')).toBeNull();
    expect(compareRequests().length).toBe(0);
  });

  it('clickingCandidate_thenCompareAgain_showsReport', async () => {
    type('a', 'raul');
    type('b', 'suzuki');
    compareButton().click();
    searchFor('raul').flush({ total: 2, truncated: false, players: [RANGEL, RANGEL2] });
    searchFor('suzuki').flush(one(SUZUKI));
    await settle();

    const rows = (q('candidates-a') as HTMLElement).querySelectorAll('[data-testid="candidate-row"]');
    (rows[1] as HTMLElement).click();
    await settle();
    expect(q('candidates-a')).toBeNull();
    expect(input('a').value).toBe('Raul RANGEL2');

    compareButton().click();
    expect(searches().length).toBe(0);
    const req = http.expectOne((r) => r.url === '/api/players/compare');
    expect(req.request.params.get('playerA')).toBe('3');
    expect(req.request.params.get('playerB')).toBe('2');
    req.flush(report([line({})]));
    await settle();
    expect(q('comparison')).not.toBeNull();
  });

  it('truncatedCandidates_showsRefineMessage', async () => {
    type('a', 'a');
    type('b', 'suzuki');
    compareButton().click();
    searchFor('a').flush({ total: 80, truncated: true, players: [RANGEL, RANGEL2] });
    searchFor('suzuki').flush(one(SUZUKI));
    await settle();
    expect(q('candidates-a')).not.toBeNull();
    expect(el.textContent).toContain('More than 50 players match: please refine your search.');
  });

  it('editingField_clearsChosenCandidatesErrorAndReport', async () => {
    await compareWith(report([line({})]));
    expect(q('comparison')).not.toBeNull();
    type('a', 'rangel again');
    expect(q('comparison')).toBeNull();

    // chosen of A cleared: A is searched again, B stays chosen (no new search for B)
    compareButton().click();
    searchFor('rangel again').flush({ total: 0, truncated: false, players: [] });
    await settle();
    expect(searches().length).toBe(0);
    expect(q('error-a')?.textContent?.trim()).toBe('Player not found.');

    type('a', 'raul');
    expect(q('error-a')).toBeNull();

    compareButton().click();
    searchFor('raul').flush({ total: 2, truncated: false, players: [RANGEL, RANGEL2] });
    await settle();
    expect(q('candidates-a')).not.toBeNull();
    type('a', 'rau');
    expect(q('candidates-a')).toBeNull();
  });

  it('searchError_showsApiOrGenericMessage', async () => {
    type('a', 'rangel');
    type('b', 'suzuki');
    compareButton().click();
    searchFor('rangel').flush({ error: 'Search is broken.' }, { status: 400, statusText: 'Bad Request' });
    searchFor('suzuki').error(new ProgressEvent('error'));
    await settle();
    expect(q('error-a')?.textContent?.trim()).toBe('Search is broken.');
    expect(q('error-b')?.textContent?.trim()).toBe('Unable to reach the server. Please try again later.');
    expect(q('comparison')).toBeNull();
  });

  it('samePlayerTwice_showsApiError', async () => {
    type('a', 'rangel');
    type('b', 'raul rangel');
    compareButton().click();
    searchFor('rangel').flush(one(RANGEL));
    searchFor('raul rangel').flush(one(RANGEL));
    http
      .expectOne((r) => r.url === '/api/players/compare')
      .flush({ error: 'The two players must be different.' }, { status: 400, statusText: 'Bad Request' });
    await settle();
    expect(pageMessage()?.textContent?.trim()).toBe('The two players must be different.');
    expect(pageMessage()?.classList.contains('message')).toBe(true);
    expect(q('comparison')).toBeNull();
  });

  it('compareUnreachable_showsGenericMessage', async () => {
    type('a', 'rangel');
    type('b', 'suzuki');
    compareButton().click();
    searchFor('rangel').flush(one(RANGEL));
    searchFor('suzuki').flush(one(SUZUKI));
    http.expectOne((r) => r.url === '/api/players/compare').error(new ProgressEvent('error'));
    await settle();
    expect(pageMessage()?.textContent?.trim()).toBe('Unable to reach the server. Please try again later.');
  });

  it('newCompare_cancelsPendingRequests', () => {
    type('a', 'rangel');
    type('b', 'suzuki');
    compareButton().click();
    const first = searchFor('rangel');
    const second = searchFor('suzuki');
    compareButton().click();
    expect(first.cancelled).toBe(true);
    expect(second.cancelled).toBe(true);
    const fresh = searches();
    expect(fresh.length).toBe(2);
    fresh.forEach((r) => r.flush({ total: 0, truncated: false, players: [] }));
  });

  it('displaysReport', async () => {
    await compareWith(
      report([line({})], {
        playerB: { id: 2, name: 'Zion SUZUKI', teamName: 'Japan', teamCode: 'JPN', role: 'OUTFIELD_PLAYER' },
        roleWarning: 'The comparison is not very relevant because the roles differ.',
      }),
    );
    expect(q('comparison')).not.toBeNull();
    expect(q('identity-a')?.textContent).toContain('Raul RANGEL');
    expect(q('identity-a')?.textContent).toContain('Mexico (MEX)');
    expect(q('identity-a')?.textContent).toContain('Goalkeeper');
    expect(q('identity-b')?.textContent).toContain('Japan (JPN)');
    expect(q('identity-b')?.textContent).toContain('Outfield player');
    expect(q('role-warning')?.textContent?.trim()).toBe('The comparison is not very relevant because the roles differ.');
    expect(q('role-warning')?.classList.contains('message')).toBe(true);
    expect(q('summary')?.textContent).toContain('Raul RANGEL 3 / 2 tied / 1 Zion SUZUKI');
    expect(q('verdict')?.textContent?.trim()).toBe('Raul RANGEL leads on more statistics than Zion SUZUKI.');
    expect(qa('line').length).toBe(1);
    expect(q('difference')?.textContent?.trim()).toBe('+2');
  });

  it('displaysReport without warning when roleWarning is null', async () => {
    await compareWith(report([line({})]));
    expect(q('comparison')).not.toBeNull();
    expect(q('role-warning')).toBeNull();
  });

  it('nullValue_showsNotAvailable', async () => {
    await compareWith(
      report([
        line({ key: 'rating', label: 'Rating', valueA: 7.1, valueB: null, difference: null, leader: null, available: false }),
        line({ key: 'goals', label: 'Goals', valueA: null, valueB: null, difference: null, leader: null, available: true }),
      ]),
    );
    const lines = qa('line');
    expect(lines[0].textContent).toContain('not available');
    expect(lines[0].textContent).not.toContain('7.1');
    expect(lines[0].querySelector('[data-testid="difference"]')).toBeNull();
    expect(lines[1].textContent).toContain('not available');
  });

  it('bar_leaderA', async () => {
    await compareWith(report([line({ leader: 'A' })]));
    expect(q('bar-a')).not.toBeNull();
    expect(q('bar-a')?.classList.contains('bar-a')).toBe(true);
    expect(q('bar-a')?.parentElement?.classList.contains('half-a')).toBe(true);
    expect(q('bar-b')).toBeNull();
    expect(q('bar-tie')).toBeNull();
  });

  it('bar_leaderB', async () => {
    await compareWith(report([line({ valueA: 3, valueB: 5, difference: -2, leader: 'B' })]));
    expect(q('bar-b')).not.toBeNull();
    expect(q('bar-b')?.classList.contains('bar-b')).toBe(true);
    expect(q('bar-b')?.parentElement?.classList.contains('half-b')).toBe(true);
    expect(q('bar-a')).toBeNull();
    expect(q('bar-tie')).toBeNull();
    expect(q('difference')?.textContent?.trim()).toBe('-2');
  });

  it('bar_tie', async () => {
    await compareWith(report([line({ valueA: 4, valueB: 4, difference: 0, leader: 'TIE' })]));
    expect(q('bar-tie')).not.toBeNull();
    expect(q('bar-tie')?.classList.contains('bar-tie')).toBe(true);
    expect(q('bar-tie')?.style.width).toBe('');
    expect(q('bar-a')).toBeNull();
    expect(q('bar-b')).toBeNull();
  });

  it('bar_noLeader', async () => {
    await compareWith(
      report([line({ key: 'minutesPlayed', label: 'Minutes played', valueA: 900, valueB: 800, difference: 100, leader: null })]),
    );
    expect(qa('line').length).toBe(1);
    expect(q('difference')?.textContent?.trim()).toBe('+100');
    expect(q('bar-a')).toBeNull();
    expect(q('bar-b')).toBeNull();
    expect(q('bar-tie')).toBeNull();
  });

  it('bar_unavailable', async () => {
    await compareWith(
      report([line({ valueA: 7, valueB: null, difference: null, leader: 'A', available: false })]),
    );
    expect(qa('line').length).toBe(1);
    expect(q('bar-a')).toBeNull();
    expect(q('bar-b')).toBeNull();
    expect(q('bar-tie')).toBeNull();
  });

  it('bar_width_relativeGap', async () => {
    await compareWith(report([line({ valueA: 5, valueB: 3, difference: 2, leader: 'A' })]));
    expect(q('bar-a')?.style.width).toBe('40%');
  });

  it('bar_lowerIsBetter', async () => {
    await compareWith(
      report([line({ key: 'yellowCards', label: 'Yellow cards', valueA: 4, valueB: 2, difference: 2, leader: 'B' })]),
    );
    expect(q('bar-b')).not.toBeNull();
    expect(q('bar-a')).toBeNull();
    expect(q('bar-b')?.style.width).toBe('50%');
  });

  it('bar_bothZero_noDivisionByZero', async () => {
    await compareWith(report([line({ valueA: 0, valueB: 0, difference: 0, leader: 'TIE' })]));
    expect(q('bar-tie')).not.toBeNull();
    expect(el.innerHTML).not.toContain('NaN');
  });

  it('texts_areEnglish', async () => {
    expect(el.textContent).toContain('Player comparison');
    expect(el.textContent).toContain('Player A');
    expect(el.textContent).toContain('Player B');
    expect(compareButton().textContent?.trim()).toBe('Compare');
    compareButton().click();
    await settle();
    expect(q('error-a')?.textContent?.trim()).toBe('Please enter a player name.');
    await compareWith(
      report([line({ valueA: null, valueB: 3, difference: null, leader: null, available: false })], {
        playerA: { id: 1, name: 'Raul RANGEL', teamName: 'Mexico', teamCode: 'MEX', role: 'GOALKEEPER' },
        playerB: { id: 2, name: 'Zion SUZUKI', teamName: 'Japan', teamCode: 'JPN', role: 'OUTFIELD_PLAYER' },
      }),
    );
    expect(q('identity-a')?.textContent).toContain('Goalkeeper');
    expect(q('identity-b')?.textContent).toContain('Outfield player');
    expect(q('summary')?.textContent).toContain('tied');
    expect(qa('line')[0].textContent).toContain('not available');
  });
});
