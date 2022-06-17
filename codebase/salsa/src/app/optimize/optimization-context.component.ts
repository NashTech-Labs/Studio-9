import { Component } from '@angular/core';

import config from '../config';
import { IEvent } from '../core/services/event.service';

import { OptimizationService } from './optimization.service';

@Component({
  selector: 'app-optimization-context',
  template: `
    <div class="group">
      <button type="button" class="btn btn-primary btn-alt btn-block"
        [routerLink]="['/desk', 'optimization', 'create']">
        Create New
      </button>
    </div>

    <core-project-context></core-project-context>

    <side-asset-list
      [caption]="'Optimizations'"
      icon="iconapp iconapp-models"
      [statusesDefinition]="config.optimization.status"
      [baseRoute]="['/desk', 'optimization']"
      [service]="service"
      [reloadOn]="['${IEvent.Type.UPDATE_OPTIMIZATION_LIST}']"
    ></side-asset-list>
  `,
})
export class OptimizationContextComponent {
  readonly config = config;

  constructor(
    readonly service: OptimizationService,
  ) {}
}
