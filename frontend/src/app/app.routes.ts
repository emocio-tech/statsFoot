import { Routes } from '@angular/router';
import { MatchList } from './features/matches/match-list';
import { MatchDetail } from './features/match-detail/match-detail';
import { Rankings } from './features/rankings/rankings';
import { PlayerStats } from './features/players/player-stats';
import { TeamStats } from './features/teams/team-stats';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'matchs' },
  { path: 'matchs', component: MatchList, title: 'Matchs · Analyse Foot' },
  { path: 'matchs/:id', component: MatchDetail, title: 'Notes du match · Analyse Foot' },
  { path: 'classements', component: Rankings, title: 'Classements · Analyse Foot' },
  { path: 'joueurs', component: PlayerStats, title: 'Joueurs · Analyse Foot' },
  { path: 'equipes', component: TeamStats, title: 'Équipes · Analyse Foot' },
  { path: '**', redirectTo: 'matchs' },
];
