import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PlayerSearchPage } from './player-search-page';
import { PlayerProfile, PlayerSearchResult, PlayerSummary } from '../player.models';

const RANGEL: PlayerSummary = { id: 1, name: 'Raul RANGEL', teamCode: 'MEX', teamName: 'Mexico' };

const PROFILE: PlayerProfile = {
  id: 1,
  name: 'Raul RANGEL',
  teamCode: 'MEX',
  teamName: 'Mexico',
  goalkeeperMatches: 5,
  captainMatches: 0,
  stats: {
    matchesPlayed: 5, matchesStarted: 5, minutesPlayed: 438, goals: 0, penaltyGoals: 0, ownGoals: 0,
    yellowCards: 0, redCards: 0, foulsCommitted: 0, freeKicksWon: 0, freeKicksConceded: 0,
    duelsWon: 5, duelsLost: 5, corners: 0, offsides: 0, distanceKm: 25.11, manOfTheMatchCount: 0,
    rating: 6.9, ratingWeighted: 6.9,
  },
};

// Rows generated from the shape of the contract (ids and names are synthetic placeholders).
function summaries(count: number): PlayerSummary[] {
  return Array.from({ length: count }, (_, i) => ({
    ...RANGEL,
    id: i + 1,
    name: `Player ${i + 1}`,
  }));
}

function result(players: PlayerSummary[], total = players.length, truncated = false): PlayerSearchResult {
  return { total, truncated, players };
}

describe('PlayerSearchPage', () => {
  let fixture: ComponentFixture<PlayerSearchPage>;
  let el: HTMLElement;
  let http: HttpTestingController;

  const q = (id: string) => el.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const rows = () => el.querySelectorAll('[data-testid="result-row"]');

  function search(value: string): void {
    const input = q('name-input') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    (q('search-form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const searchRequest = () => http.expectOne((r) => r.url === '/api/players');

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PlayerSearchPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PlayerSearchPage);
    el = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('should show the title, the field and the Search button', () => {
    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Player profile search');
    expect(q('name-input')).not.toBeNull();
    expect(q('search-button')?.textContent?.trim()).toBe('Search');
  });

  ['', '   ', ' \t '].forEach((value) => {
    it(`should show a message and make no HTTP call for blank input ${JSON.stringify(value)}`, async () => {
      search(value);
      await settle();
      http.expectNone(() => true);
      expect(q('message')?.textContent?.trim()).toBe('Please enter a player name.');
      expect(rows().length).toBe(0);
      expect(q('profile')).toBeNull();
    });
  });

  it('should send the input as typed to the API', () => {
    search('  sámi   Rangel ');
    const req = searchRequest();
    expect(req.request.params.get('name')).toBe('  sámi   Rangel ');
    req.flush(result([], 0));
  });

  it('should open the profile directly when there is one result', async () => {
    search('rangel');
    searchRequest().flush(result([RANGEL], 1));
    http.expectOne('/api/players/1').flush(PROFILE);
    await settle();
    expect(rows().length).toBe(0);
    expect(q('profile')).not.toBeNull();
    expect(el.textContent).toContain('Raul RANGEL');
    expect(el.textContent).toContain('Mexico (MEX)');
  });

  it('should list 16 results then open the profile on click', async () => {
    search('suz');
    searchRequest().flush(result(summaries(16), 16));
    await settle();
    expect(rows().length).toBe(16);
    expect(rows()[0].textContent).toContain('Player 1');
    expect(rows()[0].textContent).toContain('Mexico (MEX)');
    expect(rows()[0].textContent).not.toContain('GK');
    expect(q('profile')).toBeNull();
    expect(q('message')).toBeNull();

    (rows()[2] as HTMLElement).click();
    http.expectOne('/api/players/3').flush({ ...PROFILE, id: 3, name: 'Player 3' });
    await settle();
    expect(q('profile')).not.toBeNull();
    expect(q('profile')?.textContent).toContain('Player 3');
  });

  it('should show 50 rows and the refine message when truncated', async () => {
    search('a');
    searchRequest().flush(result(summaries(50), 1130, true));
    await settle();
    expect(rows().length).toBe(50);
    expect(q('message')?.textContent?.trim()).toBe('More than 50 players match: please refine your search.');
  });

  it('should show no refine message for exactly 50 results that are not truncated', async () => {
    search('ou');
    searchRequest().flush(result(summaries(50), 50, false));
    await settle();
    expect(rows().length).toBe(50);
    expect(q('message')).toBeNull();
  });

  it('should show "Player not found." for an empty result', async () => {
    search('xyzxyz');
    searchRequest().flush(result([], 0));
    await settle();
    expect(q('message')?.textContent?.trim()).toBe('Player not found.');
    expect(rows().length).toBe(0);
  });

  it('should show the API message on a 503 and stay usable', async () => {
    search('suz');
    searchRequest().flush({ error: 'Player data is currently unavailable.' }, { status: 503, statusText: 'Unavailable' });
    await settle();
    expect(q('message')?.textContent?.trim()).toBe('Player data is currently unavailable.');
    expect((q('search-button') as HTMLButtonElement).disabled).toBe(false);

    search('rangel');
    searchRequest().flush(result([RANGEL], 1));
    http.expectOne('/api/players/1').flush(PROFILE);
    await settle();
    expect(q('profile')).not.toBeNull();
    expect(q('message')).toBeNull();
  });

  it('should show the API message on a 400', async () => {
    search('x');
    searchRequest().flush({ error: 'Please enter a player name.' }, { status: 400, statusText: 'Bad Request' });
    await settle();
    expect(q('message')?.textContent?.trim()).toBe('Please enter a player name.');
  });

  it('should show a generic English message on a network error', async () => {
    search('suz');
    searchRequest().error(new ProgressEvent('error'));
    await settle();
    expect(q('message')?.textContent?.trim()).toBe('Unable to reach the server. Please try again later.');
  });

  it('should show an error when the profile request fails', async () => {
    search('suz');
    searchRequest().flush(result(summaries(16), 16));
    await settle();
    (rows()[0] as HTMLElement).click();
    http.expectOne('/api/players/1').flush({ error: 'Player not found.' }, { status: 404, statusText: 'Not Found' });
    await settle();
    expect(q('message')?.textContent?.trim()).toBe('Player not found.');
    expect(q('profile')).toBeNull();
  });

  it('should clear previous results, profile and messages on a new search', async () => {
    search('suz');
    searchRequest().flush(result(summaries(50), 100, true));
    await settle();
    (rows()[0] as HTMLElement).click();
    http.expectOne('/api/players/1').flush(PROFILE);
    await settle();
    expect(q('profile')).not.toBeNull();

    search('xyzxyz');
    await settle();
    expect(rows().length).toBe(0);
    expect(q('profile')).toBeNull();
    expect(q('message')).toBeNull();
    searchRequest().flush(result([], 0));
    await settle();
    expect(q('message')?.textContent?.trim()).toBe('Player not found.');
  });

  it('should clear previous results when the new input is blank', async () => {
    search('suz');
    searchRequest().flush(result(summaries(16), 16));
    await settle();
    expect(rows().length).toBe(16);

    search('  ');
    await settle();
    expect(rows().length).toBe(0);
    expect(q('message')?.textContent?.trim()).toBe('Please enter a player name.');
  });
});
