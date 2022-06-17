import { Component } from '@angular/core';

import { ProjectContext } from '../library/project.context';

@Component({
  selector: 'app-layout-desk',
  template: `
    <div class="container container-flex" style="min-height: 650px;">
      <div class="row row-flex">
        <div
          class="col-md-2 left-side"
          [hidden]="!sidebarOutlet.isActivated"
        >
          <div class="logo img-logo" [routerLink]="['/desk', 'library']" role="button"></div>
          <router-outlet name="side" #sidebarOutlet="outlet"></router-outlet>
        </div>
        <div class="col-md-10 brand-right-panel">
          <div class="header brand-header">
            <app-layout-desk-header></app-layout-desk-header>
          </div>
          <router-outlet></router-outlet>
        </div>
      </div>
     </div>
  `,
  providers: [
    {
      provide: ProjectContext,
      useValue: new ProjectContext(),
    },
  ],
})
export class LayoutDeskComponent {
}
