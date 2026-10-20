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
    { key: 'appearances', label: 'Appearances' },
    { key: 'starts', label: 'Starts' },
    { key: 'minutesPlayed', label: 'Minutes played' },
    { key: 'goals', label: 'Goals' },
    { key: 'assists', label: 'Assists' },
    { key: 'ownGoals', label: 'Own goals' },
    { key: 'shots', label: 'Shots' },
    { key: 'shotsOnTarget', label: 'Shots on target' },
    { key: 'yellowCards', label: 'Yellow cards' },
    { key: 'redCards', label: 'Red cards' },
    { key: 'foulsCommitted', label: 'Fouls committed' },
    { key: 'foulsSuffered', label: 'Fouls suffered' },
    { key: 'passes', label: 'Passes' },
    { key: 'passAccuracy', label: 'Pass accuracy' },
  ];

  display(value: number | null | undefined): string {
    return value === null || value === undefined ? 'not available' : String(value);
  }
}
