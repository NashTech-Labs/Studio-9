import { Component } from '@angular/core';

import config from '../config';

@Component({
  selector: 'app-play',
  template: `
    <router-outlet></router-outlet>
  `,
})
export class PlayComponent {
  config = config;
}
