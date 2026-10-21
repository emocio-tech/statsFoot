export type Stage =
  | 'GROUP'
  | 'ROUND_OF_32'
  | 'ROUND_OF_16'
  | 'QUARTER_FINAL'
  | 'SEMI_FINAL'
  | 'THIRD_PLACE'
  | 'FINAL';

export interface MatchSummary {
  id: number;
  stage: Stage;
  groupName: string | null;
  kickoffAt: string;
  stadium: string;
  city: string;
  team1Id: number;
  team1: string;
  team2Id: number;
  team2: string;
  score1: number;
  score2: number;
  extraTime: boolean;
  penalties1: number | null;
  penalties2: number | null;
  rating1: number | null;
  rating2: number | null;
}

export interface TeamRating {
  teamId: number;
  team: string;
  rating: number;
  result: 'W' | 'D' | 'L';
  points: number;
  goalsFor: number;
  goalsAgainst: number;
  wonShootout: boolean | null;
  fairPlayPoints: number;
}

export interface PlayerRating {
  playerId: number;
  teamId: number;
  name: string;
  rating: number;
  starter: boolean;
  captain: boolean;
  minutesPlayed: number;
  goals: number;
  ownGoals: number;
  decisiveGoals: number;
  yellowCards: number;
  sentOff: boolean;
  shootoutScored: number;
  shootoutMissed: number;
}

export interface MatchRatings {
  match: MatchSummary;
  teams: TeamRating[];
  players: PlayerRating[];
}

export interface PlayerRanking {
  playerId: number;
  name: string;
  team: string;
  matches: number;
  averageRating: number;
  goals: number;
  minutesPlayed: number;
}

export interface TeamRanking {
  teamId: number;
  team: string;
  matches: number;
  averageRating: number;
  points: number;
  goalsFor: number;
  goalsAgainst: number;
  fairPlayPoints: number;
}

/** Totaux d'un joueur sur le tournoi ; les stats détaillées sont null tant qu'elles n'ont pas été importées. */
export interface PlayerTournamentStats {
  playerId: number;
  player: string;
  teamId: number;
  team: string;
  matchesPlayed: number;
  matchesStarted: number;
  minutesPlayed: number;
  goals: number;
  penaltyGoals: number;
  ownGoals: number;
  yellowCards: number;
  redCards: number;
  foulsCommitted: number | null;
  freeKicksWon: number | null;
  freeKicksConceded: number | null;
  duelsWon: number | null;
  duelsLost: number | null;
  corners: number | null;
  offsides: number | null;
  distanceKm: number | null;
  manOfTheMatchCount: number | null;
  manOfTheMatch: boolean | null;
  detailedStatsMatches: number;
  rating: number;
  ratingWeighted: number | null;
  /** Champs dont les valeurs sont FICTIVES. */
  fictitiousFields: string[];
}

/** Totaux d'une équipe sur le tournoi ; les stats détaillées sont null tant qu'elles n'ont pas été importées. */
export interface TeamTournamentStats {
  teamId: number;
  team: string;
  matchesPlayed: number;
  wins: number;
  draws: number;
  losses: number;
  points: number;
  goalsFor: number;
  goalsAgainst: number;
  yellowCards: number;
  redCards: number;
  fairPlayPoints: number;
  foulsCommitted: number | null;
  freeKicksWon: number | null;
  freeKicksConceded: number | null;
  duelsWon: number | null;
  duelsLost: number | null;
  corners: number | null;
  offsides: number | null;
  distanceKm: number | null;
  detailedStatsMatches: number;
  rating: number;
  /** Champs dont les valeurs sont FICTIVES. */
  fictitiousFields: string[];
}
