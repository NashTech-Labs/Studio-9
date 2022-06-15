import { Component } from '@angular/core';

import config from '../config';
import { IEvent } from '../core/services/event.service';

import { PipelineService } from './pipeline.service';

@Component({
  selector: 'app-canvas-context',
  template: `
    <div class="group">
      <button type="button"
        class="btn btn-primary btn-alt btn-block"
        [routerLink]="['/desk/pipelines/create']"
      >Create Pipeline
      </button>
    </div>

    <core-project-context></core-project-context>

    <side-asset-list
      [caption]="'Pipelines'"
      icon="iconapp iconapp-visuals"
      [baseRoute]="['/desk', 'pipelines']"
      [service]="pipelines"
      [reloadOn]="['${IEvent.Type.UPDATE_PIPELINE_LIST}']"
      [actions]="{'edit': 'Edit'}"
    ></side-asset-list>
  `,
})
export class PipelineContextComponent {
  readonly config = config;

  constructor(
    readonly pipelines: PipelineService,
  ) {
  }
}

