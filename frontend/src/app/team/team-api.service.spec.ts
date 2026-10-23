import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TeamApiService } from './team-api.service';
import { TeamComparison, TeamSearchResult } from './team.models';

const SEARCH_RESULT: TeamSearchResult = {
  total: 1,
  teams: [{ id: 37, name: 'Argentina', code: 'ARG' }],
};

const COMPARISON: TeamComparison = {
  teamA: { id: 37, name: 'Argentina', code: 'ARG' },
  teamB: { id: 38, name: 'Algeria', code: 'ALG' },
  lines: [],
  summary: { leadsA: 0, leadsB: 0, ties: 0 },
  verdict: 'Argentina and Algeria are level.',
};

describe('TeamApiService', () => {
  let service: TeamApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TeamApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('search sends GET /api/teams with the name parameter', () => {
    let result: TeamSearchResult | undefined;
    service.search('arg').subscribe((r) => (result = r));

    const req = http.expectOne((r) => r.url === '/api/teams');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('name')).toBe('arg');
    req.flush(SEARCH_RESULT);

    expect(result).toEqual(SEARCH_RESULT);
  });

  it('compare sends GET /api/teams/compare with teamA and teamB', () => {
    let result: TeamComparison | undefined;
    service.compare(37, 38).subscribe((r) => (result = r));

    const req = http.expectOne((r) => r.url === '/api/teams/compare');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('teamA')).toBe('37');
    expect(req.request.params.get('teamB')).toBe('38');
    req.flush(COMPARISON);

    expect(result).toEqual(COMPARISON);
  });
});
