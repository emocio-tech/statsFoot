import { Stage } from './rating';

export type AnalyseScope = 'PLAYER' | 'TEAM';

export type AnalyseStatus = 'ANSWERED' | 'AMBIGUOUS' | 'NOT_RECOGNIZED' | 'NO_DATA' | 'FOUND' | 'CHOOSE';

/** Une question du catalogue de l'onglet Analyse. */
export interface AnalyseQuestion {
  id: string;
  label: string;
  scope: AnalyseScope;
  fictitious: boolean;
}

/** Une ligne de classement ; `team` est null pour une équipe. */
export interface RankedEntry {
  rank: number;
  scope: AnalyseScope;
  id: number;
  name: string;
  team: string | null;
  value: number | null;
  unit: string;
  tieBreakValue: number | null;
  valueLabel: string | null;
}

/** Comment une question libre a été comprise : par les mots-clés ou par l'assistant (tool Claude rank). */
export interface AnalyseInterpretation {
  source: 'KEYWORDS' | 'ASSISTANT';
  understoodAs: string | null;
}

/** L'assistant est-il configuré (clé LLM_KEY côté serveur) ? */
export interface AssistantStatus {
  enabled: boolean;
}

export interface AnalyseAnswer {
  status: AnalyseStatus;
  question: AnalyseQuestion | null;
  explanation: string | null;
  fictitious: boolean;
  winners: RankedEntry[];
  top: RankedEntry[];
  candidates: AnalyseQuestion[];
  /** null pour une suggestion cliquée. */
  interpretation: AnalyseInterpretation | null;
}

/** Rapport complet d'un joueur ; `fictitiousFields` liste les champs FICTIFS de `totals`. */
export interface PlayerReport {
  playerId: number;
  name: string;
  teamId: number;
  team: string;
  goalkeeper: boolean;
  totals: PlayerTotals;
  matches: PlayerMatchLine[];
  bestMatch: PlayerMatchLine | null;
  ranks: RankLine[];
  fictitiousFields: string[];
}

export interface PlayerTotals {
  matchesPlayed: number;
  matchesStarted: number;
  minutesPlayed: number;
  goals: number;
  penaltyGoals: number;
  ownGoals: number;
  decisiveGoals: number;
  assists: number | null;
  yellowCards: number;
  secondYellows: number;
  directReds: number;
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
  manOfTheMatchCount: number | null;
  rating: number;
  ratingWeighted: number | null;
}

export interface PlayerMatchLine {
  matchId: number;
  kickoffAt: string;
  stage: Stage;
  opponent: string;
  score: string;
  minutesPlayed: number;
  goals: number;
  assists: number;
  yellowCards: number;
  sentOff: boolean;
  rating: number;
}

/** Rang sur un critère ; `rank` null = non classé. */
export interface RankLine {
  questionId: string;
  label: string;
  rank: number | null;
  outOf: number;
}

/** Rapport complet d'une équipe ; `fictitiousFields` liste les champs FICTIFS de `totals`. */
export interface TeamReport {
  teamId: number;
  team: string;
  totals: TeamTotals;
  matches: TeamMatchLine[];
  squad: SquadLine[];
  topScorer: SquadLine | null;
  bestPlayer: SquadLine | null;
  fictitiousFields: string[];
}

export interface TeamTotals {
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
  rating: number;
  stageReached: Stage;
  finalPosition: number;
}

export interface TeamMatchLine {
  matchId: number;
  kickoffAt: string;
  stage: Stage;
  opponent: string;
  score: string;
  penalties: string | null;
  result: 'W' | 'D' | 'L';
  rating: number;
}

export interface SquadLine {
  playerId: number;
  name: string;
  goalkeeper: boolean;
  matchesPlayed: number;
  minutesPlayed: number;
  goals: number;
  assists: number | null;
  yellowCards: number;
  redCards: number;
  ratingWeighted: number | null;
}
