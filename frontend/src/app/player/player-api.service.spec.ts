import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PlayerApiService } from './player-api.service';
import { PlayerProfile, PlayerSearchResult } from './player.models';

const SEARCH_RESULT: PlayerSearchResult = {
  total: 1,
  truncated: false,
  players: [{ id: 'P-0001', name: 'Sami Suzuki', teamCode: 'ESP', teamName: 'Spain', position: 'GK' }],
};

const PROFILE: PlayerProfile = {
  id: 'P-0001',
  name: 'Sami Suzuki',
  teamCode: 'ESP',
  teamName: 'Spain',
  shirtNumber: 1,
  position: 'GK',
  positionDetail: 'GK',
  birthDate: '2006-11-03',
  age: 19,
  club: 'Club América',
  heightCm: 190,
  preferredFoot: 'right',
  captain: false,
  stats: {
    appearances: 0, starts: 0, minutesPlayed: 0, goals: 0, assists: 0, ownGoals: 0,
    shots: 0, shotsOnTarget: 0, yellowCards: 0, redCards: 0,
    foulsCommitted: 0, foulsSuffered: 0, passes: 0, passAccuracy: null,
  },
  goalkeeperStats: { saves: 0, goalsConceded: 0, cleanSheets: 0, penaltiesSaved: 0 },
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

  it('should call GET /api/players/P-0001 for the profile', () => {
    let result: PlayerProfile | undefined;
    service.getProfile('P-0001').subscribe((r) => (result = r));

    const req = http.expectOne('/api/players/P-0001');
    expect(req.request.method).toBe('GET');
    req.flush(PROFILE);

    expect(result).toEqual(PROFILE);
    expect(result?.stats.passAccuracy).toBeNull();
  });

  it('should encode the id in the profile URL', () => {
    service.getProfile('P/0001 x').subscribe();
    http.expectOne('/api/players/P%2F0001%20x').flush(PROFILE);
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
      service.getProfile('P-0001').subscribe({ error: (e) => (error = e) });
      http.expectOne('/api/players/P-0001').flush({ error: message }, { status, statusText: 'Error' });
      expect(error?.status).toBe(status);
      expect(error?.error).toEqual({ error: message });
    });
  });
});
