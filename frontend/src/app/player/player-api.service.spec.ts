import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PlayerApiService } from './player-api.service';
import { PlayerComparison, PlayerProfile, PlayerSearchResult } from './player.models';

const SEARCH_RESULT: PlayerSearchResult = {
  total: 1,
  truncated: false,
  players: [{ id: 1, name: 'Raul RANGEL', teamCode: 'MEX', teamName: 'Mexico' }],
};

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
    rating: 6.9, ratingWeighted: null,
  },
};

describe('PlayerApiService', () => {
  let service: PlayerApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PlayerApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should call GET /api/players with the encoded name', () => {
    let result: PlayerSearchResult | undefined;
    service.search('sámi & co').subscribe((r) => (result = r));

    const req = http.expectOne((r) => r.url === '/api/players');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('name')).toBe('sámi & co');
    expect(req.request.urlWithParams).toBe('/api/players?name=s%C3%A1mi%20%26%20co');
    req.flush(SEARCH_RESULT);

    expect(result).toEqual(SEARCH_RESULT);
  });

  it('should call GET /api/players/1 for the profile', () => {
    let result: PlayerProfile | undefined;
    service.getProfile(1).subscribe((r) => (result = r));

    const req = http.expectOne('/api/players/1');
    expect(req.request.method).toBe('GET');
    req.flush(PROFILE);

    expect(result).toEqual(PROFILE);
    expect(result?.stats.ratingWeighted).toBeNull();
  });

  it('compare_sendsGetWithParams', () => {
    const comparison: PlayerComparison = {
      playerA: { id: 1, name: 'Raul RANGEL', teamName: 'Mexico', teamCode: 'MEX', role: 'GOALKEEPER' },
      playerB: { id: 2, name: 'Other PLAYER', teamName: 'Brazil', teamCode: 'BRA', role: 'OUTFIELD_PLAYER' },
      lines: [],
      summary: { leadsA: 0, leadsB: 0, ties: 0 },
      roleWarning: null,
      verdict: 'Level.',
    };
    let result: PlayerComparison | undefined;
    service.compare(1, 2).subscribe((r) => (result = r));

    const req = http.expectOne((r) => r.url === '/api/players/compare');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('playerA')).toBe('1');
    expect(req.request.params.get('playerB')).toBe('2');
    req.flush(comparison);

    expect(result).toEqual(comparison);
  });

  const errorCases: [number, string][] = [
    [400, 'Please enter a player name.'],
    [404, 'Player not found.'],
    [503, 'Player data is currently unavailable.'],
  ];

  errorCases.forEach(([status, message]) => {
    it(`should propagate a ${status} error from search`, () => {
      let error: { status: number; error: { error: string } } | undefined;
      service.search('x').subscribe({ error: (e) => (error = e) });
      http.expectOne((r) => r.url === '/api/players').flush({ error: message }, { status, statusText: 'Error' });
      expect(error?.status).toBe(status);
      expect(error?.error).toEqual({ error: message });
    });

    it(`should propagate a ${status} error from getProfile`, () => {
      let error: { status: number; error: { error: string } } | undefined;
      service.getProfile(1).subscribe({ error: (e) => (error = e) });
      http.expectOne('/api/players/1').flush({ error: message }, { status, statusText: 'Error' });
      expect(error?.status).toBe(status);
      expect(error?.error).toEqual({ error: message });
    });
  });
});
