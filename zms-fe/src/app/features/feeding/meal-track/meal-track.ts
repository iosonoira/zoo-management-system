import { Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Icon } from '../../../core/ui/icon/icon';
import { MealSlot } from './meal-slots';

/**
 * Today's meals of one plan: one cell per scheduled time, in one pane divided by hairlines.
 * The state is always an icon and a word; the next unrecorded meal also gets an ink ring.
 */
@Component({
  selector: 'app-meal-track',
  imports: [DatePipe, Icon],
  templateUrl: './meal-track.html',
  styleUrl: './meal-track.scss',
})
export class MealTrack {
  readonly slots = input.required<readonly MealSlot[]>();
}
