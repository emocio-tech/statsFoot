import { Routes } from '@angular/router';
import { MatchList } from './features/matches/match-list';
import { MatchDetail } from './features/match-detail/match-detail';
import { Rankings } from './features/rankings/rankings';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'matchs' },
  { path: 'matchs', component: MatchList, title: 'Matchs · Analyse Foot' },
  { path: 'matchs/:id', component: MatchDetail, title: 'Notes du match · Analyse Foot' },
  { path: 'classements', component: Rankings, title: 'Classements · Analyse Foot' },
  { path: '**', redirectTo: 'matchs' },
];
