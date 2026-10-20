export interface PlayerSummary {
  id: string;
  name: string;
  teamCode: string;
  teamName: string;
  position: string;
}

export interface PlayerSearchResult {
  total: number;
  truncated: boolean;
  players: PlayerSummary[];
}

export interface PlayerStats {
  appearances: number;
  starts: number;
  minutesPlayed: number;
  goals: number;
  assists: number;
  ownGoals: number;
  shots: number;
  shotsOnTarget: number;
  yellowCards: number;
  redCards: number;
  foulsCommitted: number;
  foulsSuffered: number;
  passes: number;
  passAccuracy: number | null;
}

export interface GoalkeeperStats {
  saves: number;
  goalsConceded: number;
  cleanSheets: number;
  penaltiesSaved: number;
}

export interface PlayerProfile {
  id: string;
  name: string;
  teamCode: string;
  teamName: string;
  shirtNumber: number;
  position: string;
  positionDetail: string;
  birthDate: string;
  age: number;
  club: string;
  heightCm: number;
  preferredFoot: string;
  captain: boolean;
  stats: PlayerStats;
  goalkeeperStats?: GoalkeeperStats;
}
