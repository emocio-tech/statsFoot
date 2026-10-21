import { Component, input } from '@angular/core';
import { PlayerProfile, PlayerStats } from '../player.models';

interface StatLine {
  key: keyof PlayerStats;
  label: string;
}

@Component({
  selector: 'app-player-profile-card',
  templateUrl: './player-profile-card.html',
  styleUrl: './player-profile-card.css',
})
export class PlayerProfileCard {
  readonly profile = input.required<PlayerProfile>();

  readonly statLines: StatLine[] = [
    { key: 'matchesPlayed', label: 'Matches played' },
    { key: 'matchesStarted', label: 'Matches started' },
    { key: 'minutesPlayed', label: 'Minutes played' },
    { key: 'goals', label: 'Goals' },
    { key: 'penaltyGoals', label: 'Penalty goals' },
    { key: 'ownGoals', label: 'Own goals' },
    { key: 'yellowCards', label: 'Yellow cards' },
    { key: 'redCards', label: 'Red cards' },
    { key: 'foulsCommitted', label: 'Fouls committed' },
    { key: 'freeKicksWon', label: 'Free kicks won' },
    { key: 'freeKicksConceded', label: 'Free kicks conceded' },
    { key: 'duelsWon', label: 'Duels won' },
    { key: 'duelsLost', label: 'Duels lost' },
    { key: 'corners', label: 'Corners' },
    { key: 'offsides', label: 'Offsides' },
    { key: 'distanceKm', label: 'Distance (km)' },
    { key: 'manOfTheMatchCount', label: 'Man of the match' },
    { key: 'rating', label: 'Rating' },
    { key: 'ratingWeighted', label: 'Rating (weighted by minutes)' },
  ];

  display(value: number | null | undefined): string {
    return value === null || value === undefined ? 'not available' : String(value);
  }
}
