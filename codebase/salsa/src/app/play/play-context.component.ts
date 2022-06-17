import { Component } from '@angular/core';

import config from '../config';
import { Feature } from '../core/interfaces/feature-toggle.interface';
import { IEvent } from '../core/services/event.service';

import { CVPredictionService } from './cv-prediction.service';
import { PredictionService } from './prediction.service';
import { ReplayService } from './replay.service';

@Component({
  selector: 'app-play-context',
  template: `
    <div class="group">
        <button type="button" class="btn btn-primary btn-alt btn-block" [routerLink]="['/desk', 'play', 'create']">
          Create New
        </button>
    </div>

    <core-project-context></core-project-context>

    <side-asset-list
      *featureToggle="'${Feature.COMPOSE_MODULE}'"
      [caption]="'Flows'"
      icon="iconapp iconapp-flows"
      [statusesDefinition]="config.replay.status"
      [baseRoute]="['/desk', 'play','replays']"
      [service]="replayService"
      [reloadOn]="['${IEvent.Type.UPDATE_REPLAY_LIST}']"
    ></side-asset-list>
    <side-asset-list
      *featureToggle="['${Feature.TRAIN_MODULE}', '${Feature.TRAIN_MODELS}']"
      [caption]="'Predictions'"
      icon="iconapp iconapp-models"
      [statusesDefinition]="config.prediction.status"
      [baseRoute]="['/desk', 'play','predictions']"
      [service]="predictionService"
      [reloadOn]="['${IEvent.Type.UPDATE_PREDICTION_LIST}']"
    ></side-asset-list>
    <side-asset-list
      *featureToggle="['${Feature.TRAIN_MODULE}', '${Feature.TRAIN_CV_MODELS}']"
      [caption]="'CV Predictions'"
      icon="glyphicon glyphicon-blackboard"
      [statusesDefinition]="config.cvPrediction.status"
      [baseRoute]="['/desk', 'play','cv-predictions']"
      [service]="cvPredictionService"
      [reloadOn]="['${IEvent.Type.UPDATE_CV_PREDICTION_LIST}']"
    ></side-asset-list>
  `,
})
export class PlayContextComponent {
  readonly config = config;

  constructor(
    readonly replayService: ReplayService,
    readonly predictionService: PredictionService,
    readonly cvPredictionService: CVPredictionService,
  ) {}
}
