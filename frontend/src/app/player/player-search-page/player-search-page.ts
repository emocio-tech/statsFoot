import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { PlayerApiService } from '../player-api.service';
import { PlayerProfile, PlayerSummary } from '../player.models';
import { GENERIC_ERROR, PlayerPicker } from '../player-picker/player-picker';
import { PlayerProfileCard } from '../player-profile-card/player-profile-card';

@Component({
  selector: 'app-player-search-page',
  imports: [PlayerPicker, PlayerProfileCard],
  templateUrl: './player-search-page.html',
  styleUrl: './player-search-page.css',
})
export class PlayerSearchPage {
  private readonly api = inject(PlayerApiService);
  private request?: Subscription;

  readonly profile = signal<PlayerProfile | null>(null);
  readonly message = signal<string | null>(null);

  onSearchStarted(): void {
    this.request?.unsubscribe();
    this.profile.set(null);
    this.message.set(null);
  }

  openProfile(player: PlayerSummary): void {
    this.request?.unsubscribe();
    this.profile.set(null);
    this.message.set(null);
    this.request = this.api.getProfile(player.id).subscribe({
      next: (profile) => this.profile.set(profile),
      error: (error) => this.message.set(this.errorMessage(error)),
    });
  }

  private errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && typeof error.error?.error === 'string') {
      return error.error.error;
    }
    return GENERIC_ERROR;
  }
}
