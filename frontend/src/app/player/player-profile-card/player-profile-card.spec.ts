import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PlayerProfileCard } from './player-profile-card';
import { PlayerProfile } from '../player.models';

const GOALKEEPER: PlayerProfile = {
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

// Shaped like the contract: outfield player, captain, no goalkeeperStats key.
const CAPTAIN: PlayerProfile = {
  id: 'P-0005',
  name: 'Sample Captain',
  teamCode: 'ESP',
  teamName: 'Spain',
  shirtNumber: 10,
  position: 'FW',
  positionDetail: 'ST',
  birthDate: '1998-05-17',
  age: 28,
  club: 'Sample Club',
  heightCm: 181,
  preferredFoot: 'left',
  captain: true,
  stats: {
    appearances: 3, starts: 2, minutesPlayed: 215, goals: 4, assists: 1, ownGoals: 0,
    shots: 11, shotsOnTarget: 6, yellowCards: 1, redCards: 0,
    foulsCommitted: 5, foulsSuffered: 7, passes: 98, passAccuracy: 81.5,
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
    const el = render(CAPTAIN);
    expect(text(el, 'name')).toBe('Sample Captain');
    expect(text(el, 'team')).toBe('Spain (ESP)');
    expect(text(el, 'shirtNumber')).toBe('10');
    expect(text(el, 'position')).toBe('FW');
    expect(text(el, 'positionDetail')).toBe('ST');
    expect(text(el, 'birthDate')).toBe('1998-05-17');
    expect(text(el, 'age')).toBe('28');
    expect(text(el, 'club')).toBe('Sample Club');
    expect(text(el, 'height')).toContain('181');
    expect(text(el, 'preferredFoot')).toBe('left');
  });

  it('should show the 14 statistics with readable labels', () => {
    const el = render(CAPTAIN);
    const expected: [string, string, string][] = [
      ['appearances', 'Appearances', '3'],
      ['starts', 'Starts', '2'],
      ['minutesPlayed', 'Minutes played', '215'],
      ['goals', 'Goals', '4'],
      ['assists', 'Assists', '1'],
      ['ownGoals', 'Own goals', '0'],
      ['shots', 'Shots', '11'],
      ['shotsOnTarget', 'Shots on target', '6'],
      ['yellowCards', 'Yellow cards', '1'],
      ['redCards', 'Red cards', '0'],
      ['foulsCommitted', 'Fouls committed', '5'],
      ['foulsSuffered', 'Fouls suffered', '7'],
      ['passes', 'Passes', '98'],
      ['passAccuracy', 'Pass accuracy', '81.5'],
    ];
    expect(el.querySelectorAll('[data-testid^="stat-label-"]').length).toBe(14);
    for (const [key, label, value] of expected) {
      expect(text(el, `stat-label-${key}`)).toBe(label);
      expect(text(el, `stat-${key}`)).toContain(value);
    }
  });

  it('should show "not available" for a null pass accuracy', () => {
    const el = render(GOALKEEPER);
    expect(text(el, 'stat-passAccuracy')).toBe('not available');
  });

  it('should show 0 for a zero statistic', () => {
    const el = render(GOALKEEPER);
    expect(text(el, 'stat-goals')).toBe('0');
    expect(text(el, 'stat-minutesPlayed')).toBe('0');
  });

  it('should show the goalkeeper block for a goalkeeper', () => {
    const el = render(GOALKEEPER);
    expect(el.querySelector('[data-testid="goalkeeper-block"]')).not.toBeNull();
    expect(text(el, 'gk-saves')).toBe('0');
    expect(text(el, 'gk-goalsConceded')).toBe('0');
    expect(text(el, 'gk-cleanSheets')).toBe('0');
    expect(text(el, 'gk-penaltiesSaved')).toBe('0');
  });

  it('should show goalkeeper values', () => {
    const el = render({ ...GOALKEEPER, goalkeeperStats: { saves: 7, goalsConceded: 2, cleanSheets: 1, penaltiesSaved: 1 } });
    expect(text(el, 'gk-saves')).toBe('7');
    expect(text(el, 'gk-goalsConceded')).toBe('2');
  });

  it('should not render the goalkeeper block for an outfield player', () => {
    const el = render(CAPTAIN);
    expect(el.querySelector('[data-testid="goalkeeper-block"]')).toBeNull();
  });

  it('should indicate the captain', () => {
    expect(text(render(CAPTAIN), 'captain')).toBe('yes');
    expect(text(render(GOALKEEPER), 'captain')).toBe('no');
  });
});
