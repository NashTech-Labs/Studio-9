import { Component } from '@angular/core';

@Component({
  selector: 'app-root',
  template: `
    <app-block></app-block>
    <router-outlet></router-outlet>
    <notification-list></notification-list>
  `,
})
export class AppComponent {
}
