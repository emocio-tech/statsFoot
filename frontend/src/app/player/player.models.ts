export interface PlayerSummary {
  id: number;
  name: string;
  teamCode: string;
  teamName: string;
}

export interface PlayerSearchResult {
  total: number;
  truncated: boolean;
  players: PlayerSummary[];
}

export interface PlayerStats {
  matchesPlayed: number | null;
  matchesStarted: number | null;
  minutesPlayed: number | null;
  goals: number | null;
  penaltyGoals: number | null;
  ownGoals: number | null;
  yellowCards: number | null;
  redCards: number | null;
  foulsCommitted: number | null;
  freeKicksWon: number | null;
  freeKicksConceded: number | null;
  duelsWon: number | null;
  duelsLost: number | null;
  corners: number | null;
  offsides: number | null;
  distanceKm: number | null;
  manOfTheMatchCount: number | null;
  rating: number | null;
  ratingWeighted: number | null;
}

export type PlayerRole = 'GOALKEEPER' | 'OUTFIELD_PLAYER';

export type Leader = 'A' | 'B' | 'TIE';

export interface ComparedPlayer {
  id: number;
  name: string;
  teamName: string;
  teamCode: string;
  role: PlayerRole;
}

export interface ComparisonLine {
  key: string;
  label: string;
  valueA: number | null;
  valueB: number | null;
  difference: number | null;
  leader: Leader | null;
  available: boolean;
}

export interface ComparisonSummary {
  leadsA: number;
  leadsB: number;
  ties: number;
}

export interface PlayerComparison {
  playerA: ComparedPlayer;
  playerB: ComparedPlayer;
  lines: ComparisonLine[];
  summary: ComparisonSummary;
  roleWarning: string | null;
  verdict: string;
}

export interface PlayerProfile {
  id: number;
  name: string;
  teamCode: string;
  teamName: string;
  goalkeeperMatches: number;
  captainMatches: number;
  stats: PlayerStats;
}
