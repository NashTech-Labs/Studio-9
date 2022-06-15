import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';

import { Observable } from 'rxjs/Observable';

import { DeskRoute } from '../core/desk.route';
import { IAsset } from '../core/interfaces/common.interface';
import { Feature } from '../core/interfaces/feature-toggle.interface';

import { PlayContextComponent } from './play-context.component';
import { PlayCreateComponent } from './play-create.component';
import { PlayCVPredictionViewComponent } from './play-cv-prediction-view.component';
import { PlayPredictionViewComponent } from './play-prediction-view.component';
import { PlayReplayViewComponent } from './play-replay-view.component';
import { IReplay } from './replay.interface';
import { ReplayService } from './replay.service';

@Injectable()
export class ReplayResolve implements Resolve<IReplay> {

  constructor(private replays: ReplayService) {
  }

  resolve(route: ActivatedRouteSnapshot): Observable<IReplay> {
    let replayId = route.params['replayId'];
    return this.replays.get(replayId);
  }
}

export const playRoutes: DeskRoute[] = [
  {
    path: 'play',
    sideComponent: PlayContextComponent,
    features: [Feature.PLAY_MODULE],
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'create',
      },
      {
        path: 'create',
        component: PlayCreateComponent,
      },
      {
        path: 'predictions/:predictionId',
        component: PlayPredictionViewComponent,
      },
      {
        path: 'cv-predictions/:predictionId',
        component: PlayCVPredictionViewComponent,
      },
      {
        path: 'replays/:replayId',
        component: PlayReplayViewComponent,
        resolve: { replay: ReplayResolve },
      },
    ],
  },
];

export const playModuleAssetURLMap = {
  [IAsset.Type.PREDICTION]: ['/desk', 'play', 'predictions'],
  [IAsset.Type.CV_PREDICTION]: ['/desk', 'play', 'cv-predictions'],
  [IAsset.Type.REPLAY]: ['/desk', 'play', 'replays'],
};
