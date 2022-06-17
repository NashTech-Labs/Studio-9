import { Component } from '@angular/core';

import { versionInfo } from '../version-info';

@Component({
  selector: 'app-layout-splash',
  template: `
    <div class="container container-splash">
      <div class="row">
        <div class="col-md-12">
          <div class="logo img-logo center-block"></div>
        </div>
        <div class="col-md-12 text-center" [hidden]="true">
          <span class="app-version">Version {{ appVersion }}</span>
        </div>
      </div>
      <ng-content></ng-content>
    </div>
  `,
})
export class LayoutSplashComponent {
  readonly appVersion: string = versionInfo.semverString || versionInfo.tag;
}

