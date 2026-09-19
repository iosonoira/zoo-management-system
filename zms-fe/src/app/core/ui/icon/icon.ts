import { Component, input } from '@angular/core';

export type IconName =
  | 'search'
  | 'close'
  | 'chevron-right'
  | 'arrow-left'
  | 'arrow-right'
  | 'check'
  | 'eye'
  | 'cross'
  | 'ribbon'
  | 'danger'
  | 'terrestrial'
  | 'aquatic'
  | 'amphibious'
  | 'lock'
  | 'transfer'
  | 'status'
  | 'user'
  | 'alert';

/** Pictogram set drawn for the signage system: 24px grid, 2px stroke, round joins. */
@Component({
  selector: 'app-icon',
  templateUrl: './icon.html',
  styleUrl: './icon.scss',
  host: { 'aria-hidden': 'true' },
})
export class Icon {
  readonly name = input.required<IconName>();
}
