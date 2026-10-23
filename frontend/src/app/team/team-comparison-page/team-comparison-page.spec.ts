import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ComparisonLine } from '../../player/player.models';
import { TeamComparison, TeamSummary } from '../team.models';
import { TeamComparisonPage } from './team-comparison-page';

const ARGENTINA: TeamSummary = { id: 37, name: 'Argentina', code: 'ARG' };
const ALGERIA: TeamSummary = { id: 38, name: 'Algeria', code: 'ALG' };
const GUINEA: TeamSummary = { id: 40, name: 'Guinea', code: 'GUI' };
const EQ_GUINEA: TeamSummary = { id: 41, name: 'Equatorial Guinea', code: 'EQG' };

function line(overrides: Partial<ComparisonLine>): ComparisonLine {
  return {
    key: 'points',
    label: 'Points',
    valueA: 21,
    valueB: 4,
    difference: 17,
    leader: 'A',
    available: true,
    ...overrides,
  };
}

function report(lines: ComparisonLine[]): TeamComparison {
  return {
    teamA: { id: 37, name: 'Argentina', code: 'ARG' },
    teamB: { id: 38, name: 'Algeria', code: 'ALG' },
    lines,
    summary: { leadsA: 3, leadsB: 1, ties: 2 },
    verdict: 'Argentina leads on 3 statistics to 1 for Algeria (2 tied).',
  };
}

describe('TeamComparisonPage', () => {
  let fixture: ComponentFixture<TeamComparisonPage>;
  let el: HTMLElement;
  let http: HttpTestingController;

  const q = (id: string) => el.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const qa = (id: string) => el.querySelectorAll(`[data-testid="${id}"]`);
  const compareButton = () => q('compare-button') as HTMLButtonElement;
  const input = (side: 'a' | 'b') => q(`name-${side}`) as HTMLInputElement;
  const searchFor = (name: string) =>
    http.expectOne((r) => r.url === '/api/teams' && r.params.get('name') === name);

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function type(side: 'a' | 'b', text: string): void {
    input(side).value = text;
    input(side).dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function one(team: TeamSummary) {
    return { total: 1, teams: [team] };
  }

  async function compareWith(result: TeamComparison): Promise<void> {
    type('a', ARGENTINA.name);
    type('b', ALGERIA.name);
    compareButton().click();
    searchFor(ARGENTINA.name).flush(one(ARGENTINA));
    searchFor(ALGERIA.name).flush(one(ALGERIA));
    http.expectOne((r) => r.url === '/api/teams/compare').flush(result);
    await settle();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TeamComparisonPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(TeamComparisonPage);
    el = fixture.nativeElement;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('twoFieldsAndOneButton', () => {
    expect(el.querySelectorAll('input').length).toBe(2);
    expect(el.querySelectorAll('button').length).toBe(1);
    expect(compareButton().textContent?.trim()).toBe('Compare');
  });

  it('compare_resolvesBothNamesThenShowsReport', async () => {
    await compareWith(report([line({})]));

    expect(q('identity-a')?.textContent).toContain('Argentina');
    expect(q('identity-a')?.textContent).toContain('ARG');
    expect(q('identity-b')?.textContent).toContain('Algeria');
    expect(q('summary')?.textContent).toContain('Argentina 3 / 2 tied / 1 Algeria');
    expect(q('verdict')?.textContent).toContain('Argentina leads');
    expect(qa('line').length).toBe(1);
  });

  it('blankField_showsErrorAndSendsNoRequest', async () => {
    type('a', 'Argentina');
    compareButton().click();
    await settle();

    expect(q('error-b')?.textContent).toContain('Please enter a team name.');
    expect(q('error-a')).toBeNull();
    searchFor('Argentina').flush(one(ARGENTINA));
    http.expectNone((r) => r.url === '/api/teams/compare');
  });

  it('unknownName_showsTeamNotFoundForThatField', async () => {
    type('a', 'Atlantis');
    type('b', ALGERIA.name);
    compareButton().click();
    searchFor('Atlantis').flush({ total: 0, teams: [] });
    searchFor(ALGERIA.name).flush(one(ALGERIA));
    await settle();

    expect(q('error-a')?.textContent).toContain('Team not found.');
    expect(q('error-b')).toBeNull();
    expect(q('comparison')).toBeNull();
    http.expectNone((r) => r.url === '/api/teams/compare');
  });

  it('severalMatches_listsCandidates_thenClickAndCompareAgain', async () => {
    type('a', 'gui');
    type('b', ALGERIA.name);
    compareButton().click();
    searchFor('gui').flush({ total: 2, teams: [EQ_GUINEA, GUINEA] });
    searchFor(ALGERIA.name).flush(one(ALGERIA));
    await settle();

    expect(qa('candidate-row').length).toBe(2);
    expect(qa('candidate-row')[0].textContent).toContain('Equatorial Guinea (EQG)');
    http.expectNone((r) => r.url === '/api/teams/compare');

    (qa('candidate-row')[1] as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(input('a').value).toBe('Guinea');

    compareButton().click();
    // Team B was already resolved: only the comparison request is expected, with no new search.
    http.expectNone((r) => r.url === '/api/teams');
    const req = http.expectOne((r) => r.url === '/api/teams/compare');
    expect(req.request.params.get('teamA')).toBe('40');
    expect(req.request.params.get('teamB')).toBe('38');
    req.flush(report([line({})]));
    await settle();
    expect(q('comparison')).not.toBeNull();
  });

  it('exactNameAmongSeveralMatches_usesExactTeamWithoutList', async () => {
    type('a', ' guinea ');
    type('b', ALGERIA.name);
    compareButton().click();
    searchFor(' guinea ').flush({ total: 2, teams: [EQ_GUINEA, GUINEA] });
    searchFor(ALGERIA.name).flush(one(ALGERIA));

    const req = http.expectOne((r) => r.url === '/api/teams/compare');
    expect(req.request.params.get('teamA')).toBe('40');
    req.flush(report([line({})]));
    await settle();
    expect(qa('candidate-row').length).toBe(0);
  });

  it('editingField_clearsReportAndError', async () => {
    await compareWith(report([line({})]));
    expect(q('comparison')).not.toBeNull();

    type('a', 'Arg');

    expect(q('comparison')).toBeNull();
  });

  it('sameTeamTwice_showsApiError', async () => {
    type('a', ARGENTINA.name);
    type('b', ARGENTINA.name);
    compareButton().click();
    http.match((r) => r.url === '/api/teams').forEach((r) => r.flush(one(ARGENTINA)));
    http
      .expectOne((r) => r.url === '/api/teams/compare')
      .flush({ error: 'The two teams must be different.' }, { status: 400, statusText: 'Bad Request' });
    await settle();

    expect(q('message')?.textContent).toContain('The two teams must be different.');
    expect(q('comparison')).toBeNull();
  });

  it('unreachableServer_showsGenericMessage', async () => {
    type('a', ARGENTINA.name);
    type('b', ALGERIA.name);
    compareButton().click();
    searchFor(ARGENTINA.name).flush(one(ARGENTINA));
    searchFor(ALGERIA.name).flush(one(ALGERIA));
    http.expectOne((r) => r.url === '/api/teams/compare').error(new ProgressEvent('error'));
    await settle();

    expect(q('message')?.textContent).toContain('Unable to reach the server');
  });

  it('nullValue_showsNotAvailable_andNoBar', async () => {
    await compareWith(
      report([line({ key: 'rating', label: 'Rating', valueA: null, difference: null, leader: null, available: false })]),
    );

    expect(q('line')?.textContent).toContain('not available');
    expect(q('difference')).toBeNull();
    expect(q('bar-a')).toBeNull();
    expect(q('bar-b')).toBeNull();
    expect(q('bar-tie')).toBeNull();
  });

  it('bars_followTheLeader', async () => {
    await compareWith(
      report([
        line({ key: 'a', leader: 'A', valueA: 10, valueB: 5, difference: 5 }),
        line({ key: 'b', leader: 'B', valueA: 1, valueB: 4, difference: -3 }),
        line({ key: 'tie', leader: 'TIE', valueA: 2, valueB: 2, difference: 0 }),
        line({ key: 'none', leader: null, valueA: 9, valueB: 3, difference: 6 }),
      ]),
    );

    const lines = Array.from(qa('line')) as HTMLElement[];
    expect(lines[0].querySelector('[data-testid="bar-a"]')).not.toBeNull();
    expect(lines[1].querySelector('[data-testid="bar-b"]')).not.toBeNull();
    expect(lines[2].querySelector('[data-testid="bar-tie"]')).not.toBeNull();
    expect(lines[3].querySelector('.bar')).toBeNull();
    expect(lines[1].querySelector('[data-testid="difference"]')?.textContent).toContain('-3');
    expect(lines[0].querySelector('[data-testid="difference"]')?.textContent).toContain('+5');
  });

  it('bar_width_isTheRelativeGap_andBothZeroDoesNotDivideByZero', async () => {
    await compareWith(
      report([
        line({ key: 'a', leader: 'A', valueA: 10, valueB: 5, difference: 5 }),
        line({ key: 'zero', leader: 'TIE', valueA: 0, valueB: 0, difference: 0 }),
      ]),
    );

    expect((q('bar-a') as HTMLElement).style.width).toBe('50%');
    const page = fixture.componentInstance;
    expect(page.gapPercent(line({ valueA: 0, valueB: 0, difference: 0 }))).toBe(0);
  });
});
