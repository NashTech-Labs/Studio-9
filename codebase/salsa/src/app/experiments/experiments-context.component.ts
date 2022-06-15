import { Component } from '@angular/core';

import config from '../config';
import { IEvent } from '../core/services/event.service';

import { ExperimentService } from './experiment.service';

@Component({
  selector: 'experiments-context',
  template: `
    <div class="group">
      <button
        type="button"
        class="btn btn-primary btn-block"
        routerLinkActive
        #experimentCreateActive="routerLinkActive"
        [routerLink]="['/desk', 'experiments', 'create']"
        [ngClass]="{'btn-alt': !experimentCreateActive.isActive}"
      >
      Create Experiment
      </button>
    </div>

    <core-project-context></core-project-context>

    <side-asset-list
      [caption]="'Experiments'"
      [baseRoute]="['/desk', 'experiments']"
      [service]="experimentService"
      [statusesDefinition]="config.experiments.status"
      [icon]="'glyphicon glyphicon-education'"
      [reloadOn]="['${IEvent.Type.UPDATE_EXPERIMENT_LIST}']"
    ></side-asset-list>
  `,
})
export class ExperimentsContextComponent {
  readonly config = config;

  constructor(
    protected experimentService: ExperimentService,
  ) {
  }
}
