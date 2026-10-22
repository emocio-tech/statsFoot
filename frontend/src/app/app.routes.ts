import { Routes } from '@angular/router';
import { ChatPage } from './chat/chat-page';
import { PlayerComparisonPage } from './player/player-comparison-page/player-comparison-page';
import { PlayerSearchPage } from './player/player-search-page/player-search-page';
import { TeamComparisonPage } from './team/team-comparison-page/team-comparison-page';

export const routes: Routes = [
  { path: '', component: PlayerSearchPage, title: 'Player profile search' },
  { path: 'chat', component: ChatPage, title: 'Chat' },
  { path: 'compare', component: PlayerComparisonPage, title: 'Player comparison' },
  { path: 'compare-teams', component: TeamComparisonPage, title: 'Team comparison' },
];
