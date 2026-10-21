import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { PlayerApiService } from '../player-api.service';
import { PlayerProfile, PlayerSummary } from '../player.models';
import { PlayerProfileCard } from '../player-profile-card/player-profile-card';

const GENERIC_ERROR = 'Unable to reach the server. Please try again later.';

@Component({
  selector: 'app-player-search-page',
  imports: [PlayerProfileCard],
  templateUrl: './player-search-page.html',
  styleUrl: './player-search-page.css',
})
export class PlayerSearchPage {
  private readonly api = inject(PlayerApiService);
  private request?: Subscription;

  readonly results = signal<PlayerSummary[]>([]);
  readonly profile = signal<PlayerProfile | null>(null);
  readonly message = signal<string | null>(null);

  onSubmit(event: Event, name: string): void {
    event.preventDefault();
    this.reset();
    if (name.trim() === '') {
      this.message.set('Please enter a player name.');
      return;
    }
    this.request = this.api.search(name).subscribe({
      next: (result) => {
        if (result.total === 0 || result.players.length === 0) {
          this.message.set('Player not found.');
        } else if (result.total === 1) {
          this.openProfile(result.players[0].id);
        } else {
          this.results.set(result.players);
          if (result.truncated) {
            this.message.set('More than 50 players match: please refine your search.');
          }
        }
      },
      error: (error) => this.message.set(this.errorMessage(error)),
    });
  }

  openProfile(id: string): void {
    this.request?.unsubscribe();
    this.profile.set(null);
    this.request = this.api.getProfile(id).subscribe({
      next: (profile) => {
        this.results.set([]);
        this.message.set(null);
        this.profile.set(profile);
      },
      error: (error) => {
        this.results.set([]);
        this.message.set(this.errorMessage(error));
      },
    });
  }

  private reset(): void {
    this.request?.unsubscribe();
    this.results.set([]);
    this.profile.set(null);
    this.message.set(null);
  }

  private errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && typeof error.error?.error === 'string') {
      return error.error.error;
    }
    return GENERIC_ERROR;
  }
}
