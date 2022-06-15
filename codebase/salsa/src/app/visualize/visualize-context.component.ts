import { Component } from '@angular/core';

import config from '../config';
import { IEvent } from '../core/services/event.service';

import { DashboardService } from './dashboard.service';

@Component({
  selector: 'app-visualize-context',
  template: `
    <div class="group">
      <button type="button"
        class="btn btn-primary btn-alt btn-block"
        [routerLink]="['/desk/visualize/dashboards/create']"
      >Create New Dashboard
      </button>
    </div>

    <core-project-context></core-project-context>

    <side-asset-list
      [caption]="'Dashboards'"
      icon="iconapp iconapp-visuals"
      [statusesDefinition]="config.dashboard.status"
      [baseRoute]="['/desk', 'visualize', 'dashboards']"
      [service]="dashboards"
      [reloadOn]="['${IEvent.Type.UPDATE_DASHBOARD_LIST}']"
      [actions]="{'edit': 'Edit'}"
    ></side-asset-list>
  `,
})
export class VisualizeContextComponent {
  readonly config = config;

  constructor(
    readonly dashboards: DashboardService,
  ) {}
}

