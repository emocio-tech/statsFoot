import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PlayerPicker } from './player-picker';
import { PlayerSearchResult, PlayerSummary } from '../player.models';

const RANGEL: PlayerSummary = { id: 1, name: 'Raul RANGEL', teamCode: 'MEX', teamName: 'Mexico' };

function summaries(count: number): PlayerSummary[] {
  return Array.from({ length: count }, (_, i) => ({ ...RANGEL, id: i + 1, name: `Player ${i + 1}` }));
}

function result(players: PlayerSummary[], total = players.length, truncated = false): PlayerSearchResult {
  return { total, truncated, players };
}

describe('PlayerPicker', () => {
  let fixture: ComponentFixture<PlayerPicker>;
  let el: HTMLElement;
  let http: HttpTestingController;
  let selected: PlayerSummary[];

  const q = (id: string) => el.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const rows = () => el.querySelectorAll('[data-testid="result-row"]');
  const searchRequest = () => http.expectOne((r) => r.url === '/api/players');

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

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PlayerPicker],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PlayerPicker);
    el = fixture.nativeElement as HTMLElement;
    selected = [];
    fixture.componentInstance.playerSelected.subscribe((p) => selected.push(p));
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  describe('emitsPlayerSelected', () => {
    it('auto-selects a single result', async () => {
      search('rangel');
      searchRequest().flush(result([RANGEL], 1));
      await settle();
      expect(selected).toEqual([RANGEL]);
      expect(rows().length).toBe(0);
    });

    it('emits the clicked row when there are several results', async () => {
      search('suz');
      searchRequest().flush(result(summaries(16), 16));
      await settle();
      expect(rows().length).toBe(16);
      expect(selected).toEqual([]);

      (rows()[2] as HTMLElement).click();
      await settle();
      expect(selected).toEqual([summaries(16)[2]]);
    });
  });

  describe('refineMessage_and_errors', () => {
    it('shows the refine message when the list is truncated', async () => {
      search('a');
      searchRequest().flush(result(summaries(50), 1130, true));
      await settle();
      expect(rows().length).toBe(50);
      expect(q('message')?.textContent?.trim()).toBe('More than 50 players match: please refine your search.');
    });

    it('shows no refine message when 50 results are not truncated', async () => {
      search('ou');
      searchRequest().flush(result(summaries(50), 50, false));
      await settle();
      expect(q('message')).toBeNull();
    });

    it('shows the API message on a 503', async () => {
      search('suz');
      searchRequest().flush({ error: 'Player data is currently unavailable.' }, { status: 503, statusText: 'Unavailable' });
      await settle();
      expect(q('message')?.textContent?.trim()).toBe('Player data is currently unavailable.');
    });

    it('shows the API message on a 400', async () => {
      search('x');
      searchRequest().flush({ error: 'Please enter a player name.' }, { status: 400, statusText: 'Bad Request' });
      await settle();
      expect(q('message')?.textContent?.trim()).toBe('Please enter a player name.');
    });

    it('shows a generic English message on a network error', async () => {
      search('suz');
      searchRequest().error(new ProgressEvent('error'));
      await settle();
      expect(q('message')?.textContent?.trim()).toBe('Unable to reach the server. Please try again later.');
    });
  });
});
