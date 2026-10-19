import { Pipe, PipeTransform } from '@angular/core';
import { Stage } from '../core/models/rating';

export const STAGE_ORDER: Stage[] = [
  'GROUP',
  'ROUND_OF_32',
  'ROUND_OF_16',
  'QUARTER_FINAL',
  'SEMI_FINAL',
  'THIRD_PLACE',
  'FINAL',
];

const LABELS: Record<Stage, string> = {
  GROUP: 'Phase de groupes',
  ROUND_OF_32: '16es de finale',
  ROUND_OF_16: '8es de finale',
  QUARTER_FINAL: 'Quarts de finale',
  SEMI_FINAL: 'Demi-finales',
  THIRD_PLACE: 'Match pour la 3e place',
  FINAL: 'Finale',
};

@Pipe({ name: 'stageLabel' })
export class StageLabelPipe implements PipeTransform {
  transform(stage: Stage, groupName?: string | null): string {
    return groupName ? `Groupe ${groupName}` : LABELS[stage];
  }
}
