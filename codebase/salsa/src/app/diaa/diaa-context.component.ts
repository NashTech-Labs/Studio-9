import { Component } from '@angular/core';

import config from '../config';
import { IEvent } from '../core/services/event.service';

import { diaaConfig } from './diaa.config';
import { DIAAService } from './diaa.service';

@Component({
  selector: 'diaa-context',
  template: `
    <div class="group">
      <button type="button" class="btn btn-primary btn-alt btn-block"
        [routerLink]="['/desk', 'diaa', 'create']">
        Create New
      </button>
    </div>

    <core-project-context></core-project-context>

    <side-asset-list
      [caption]="'DIAAs'"
      icon="iconapp iconapp-models"
      [statusesDefinition]="itemConfig.status"
      [baseRoute]="['/desk', 'diaa']"
      [service]="service"
      [reloadOn]="['${IEvent.Type.UPDATE_DIAA_LIST}']"
    ></side-asset-list>
  `,
})
export class DIAAContextComponent {
  readonly config = config;
  readonly itemConfig = diaaConfig;

  constructor(
    readonly service: DIAAService,
  ) {}
}
