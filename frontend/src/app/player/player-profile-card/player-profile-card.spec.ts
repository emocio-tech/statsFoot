import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PlayerProfileCard } from './player-profile-card';
import { PlayerProfile } from '../player.models';

// Shaped like the contract: id 1, goalkeeper.
const RANGEL: PlayerProfile = {
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

// Shaped like the contract: id 316, no minutes played, null weighted rating.
const WRIGHT: PlayerProfile = {
  id: 316,
  name: 'Haji WRIGHT',
  teamCode: 'USA',
  teamName: 'USA',
  goalkeeperMatches: 0,
  captainMatches: 0,
  stats: {
    matchesPlayed: 2, matchesStarted: 0, minutesPlayed: 0, goals: 0, penaltyGoals: 0, ownGoals: 0,
    yellowCards: 0, redCards: 0, foulsCommitted: 0, freeKicksWon: 0, freeKicksConceded: 0,
    duelsWon: 0, duelsLost: 0, corners: 0, offsides: 0, distanceKm: 0, manOfTheMatchCount: 0,
    rating: 6.5, ratingWeighted: null,
  },
};

describe('PlayerProfileCard', () => {
  let fixture: ComponentFixture<PlayerProfileCard>;

  function render(profile: PlayerProfile): HTMLElement {
    fixture = TestBed.createComponent(PlayerProfileCard);
    fixture.componentRef.setInput('profile', profile);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function text(el: HTMLElement, id: string): string | undefined {
    return el.querySelector(`[data-testid="${id}"]`)?.textContent?.trim();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [PlayerProfileCard] }).compileComponents();
  });

  it('should show all identity fields', () => {
    const el = render(RANGEL);
    expect(text(el, 'name')).toBe('Raul RANGEL');
    expect(text(el, 'team')).toBe('Mexico (MEX)');
    expect(text(el, 'goalkeeperMatches')).toBe('5');
    expect(text(el, 'captainMatches')).toBe('0');
  });

  it('should show the 19 totals with readable labels', () => {
    const el = render(RANGEL);
    const expected: [string, string, string][] = [
      ['matchesPlayed', 'Matches played', '5'],
      ['matchesStarted', 'Matches started', '5'],
      ['minutesPlayed', 'Minutes played', '438'],
      ['goals', 'Goals', '0'],
      ['penaltyGoals', 'Penalty goals', '0'],
      ['ownGoals', 'Own goals', '0'],
      ['yellowCards', 'Yellow cards', '0'],
      ['redCards', 'Red cards', '0'],
      ['foulsCommitted', 'Fouls committed', '0'],
      ['freeKicksWon', 'Free kicks won', '0'],
      ['freeKicksConceded', 'Free kicks conceded', '0'],
      ['duelsWon', 'Duels won', '5'],
      ['duelsLost', 'Duels lost', '5'],
      ['corners', 'Corners', '0'],
      ['offsides', 'Offsides', '0'],
      ['distanceKm', 'Distance (km)', '25.11'],
      ['manOfTheMatchCount', 'Man of the match', '0'],
      ['rating', 'Rating', '6.9'],
      ['ratingWeighted', 'Rating (weighted by minutes)', '6.9'],
    ];
    expect(el.querySelectorAll('[data-testid^="stat-label-"]').length).toBe(19);
    for (const [key, label, value] of expected) {
      expect(text(el, `stat-label-${key}`)).toBe(label);
      expect(text(el, `stat-${key}`)).toBe(value);
    }
  });

  it('should show "not available" for a null weighted rating', () => {
    const el = render(WRIGHT);
    expect(text(el, 'stat-ratingWeighted')).toBe('not available');
    expect(text(el, 'name')).toBe('Haji WRIGHT');
    expect(text(el, 'team')).toBe('USA (USA)');
  });

  it('should show 0 for a zero statistic', () => {
    const el = render(RANGEL);
    expect(text(el, 'stat-goals')).toBe('0');
    expect(text(el, 'stat-manOfTheMatchCount')).toBe('0');
    expect(render(WRIGHT).querySelector('[data-testid="stat-minutesPlayed"]')?.textContent?.trim()).toBe('0');
  });

  it('should not show any removed item', () => {
    const el = render(RANGEL);
    const content = (el.textContent ?? '').toLowerCase();
    for (const removed of [
      'shirt number', 'position', 'birth', 'age', 'club', 'height', 'foot',
      'assists', 'shots', 'passes', 'goalkeeper statistics',
    ]) {
      expect(content).not.toContain(removed);
    }
    expect(el.querySelector('[data-testid="goalkeeper-block"]')).toBeNull();
  });
});
