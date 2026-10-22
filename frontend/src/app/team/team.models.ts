import { ComparisonLine, ComparisonSummary } from '../player/player.models';

export interface TeamSummary {
  id: number;
  name: string;
  code: string;
}

export interface TeamSearchResult {
  total: number;
  teams: TeamSummary[];
}

export interface ComparedTeam {
  id: number;
  name: string;
  code: string;
}

export interface TeamComparison {
  teamA: ComparedTeam;
  teamB: ComparedTeam;
  lines: ComparisonLine[];
  summary: ComparisonSummary;
  verdict: string;
}
