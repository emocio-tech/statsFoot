import { Routes } from '@angular/router';
import { ChatPage } from './chat/chat-page';
import { PlayerSearchPage } from './player/player-search-page/player-search-page';

export const routes: Routes = [
  { path: '', component: PlayerSearchPage, title: 'Player profile search' },
  { path: 'chat', component: ChatPage, title: 'Chat' },
];
