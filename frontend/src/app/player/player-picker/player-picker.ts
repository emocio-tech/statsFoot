import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, output, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { PlayerApiService } from '../player-api.service';
import { PlayerSummary } from '../player.models';

export const GENERIC_ERROR = 'Unable to reach the server. Please try again later.';

let nextPickerId = 0;

@Component({
  selector: 'app-player-picker',
  templateUrl: './player-picker.html',
  styleUrl: './player-picker.css',
})
export class PlayerPicker {
  private readonly api = inject(PlayerApiService);
  private request?: Subscription;

  readonly inputId = `player-name-${nextPickerId++}`;

  readonly playerSelected = output<PlayerSummary>();
  readonly searchStarted = output<void>();

  readonly results = signal<PlayerSummary[]>([]);
  readonly message = signal<string | null>(null);

  onSubmit(event: Event, name: string): void {
    event.preventDefault();
    this.request?.unsubscribe();
    this.results.set([]);
    this.message.set(null);
    this.searchStarted.emit();
    if (name.trim() === '') {
      this.message.set('Please enter a player name.');
      return;
    }
    this.request = this.api.search(name).subscribe({
      next: (result) => {
        if (result.total === 0 || result.players.length === 0) {
          this.message.set('Player not found.');
        } else if (result.total === 1) {
          this.select(result.players[0]);
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

  select(player: PlayerSummary): void {
    this.results.set([]);
    this.message.set(null);
    this.playerSelected.emit(player);
  }

  private errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && typeof error.error?.error === 'string') {
      return error.error.error;
    }
    return GENERIC_ERROR;
  }
}
