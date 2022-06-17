import { ModuleWithProviders, NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';

import { AlbumsModule } from '../albums/albums.module';
import { ChartsModule } from '../charts/charts.module';
import { ComposeModule } from '../compose/compose.module';
import config from '../config';
import { CoreUIModule } from '../core-ui/core-ui.module';
import { CoreModule } from '../core/core.module';
import { ASSET_BASE_ROUTE, IAsset } from '../core/interfaces/common.interface';
import { Feature } from '../core/interfaces/feature-toggle.interface';
import { IEvent } from '../core/services/event.service';
import { LIBRARY_SECTIONS, LibrarySectionDefinition } from '../library/library.interface';
import { TablesModule } from '../tables/tables.module';
import { TrainModule } from '../train/train.module';

import { PlayCVPredictionCreateAdvancedComponent } from './cv-prediction-create-advanced.component';
import { CVPredictionTimeSpentSummaryComponent } from './cv-prediction-time-spent-summary.component';
import { ICVPrediction, ICVPredictionStatus } from './cv-prediction.interface';
import { CVPredictionService } from './cv-prediction.service';
import { PlayContextComponent } from './play-context.component';
import { PlayCreateComponent } from './play-create.component';
import { PlayCVPredictionCreateComponent } from './play-cv-prediction-create.component';
import { PlayCVPredictionViewComponent } from './play-cv-prediction-view.component';
import { PlayPredictionCreateComponent } from './play-prediction-create.component';
import { PlayPredictionViewComponent } from './play-prediction-view.component';
import { PlayReplayCreateComponent } from './play-replay-create.component';
import { PlayReplayViewComponent } from './play-replay-view.component';
import { PlayComponent } from './play.component';
import { ReplayResolve, playModuleAssetURLMap } from './play.routes';
import { IPrediction, IPredictionStatus } from './prediction.interface';
import { PredictionService } from './prediction.service';
import { ReplayService } from './replay.service';

@NgModule({
  imports: [
    RouterModule,
    BrowserModule,
    FormsModule,
    ReactiveFormsModule,
    // deep cortex modules
    CoreModule,
    ChartsModule,
    CoreUIModule,
    TablesModule,
    ComposeModule,
    AlbumsModule,
    TrainModule,
  ],
  declarations: [
    PlayComponent,
    PlayCreateComponent,
    PlayPredictionCreateComponent,
    PlayCVPredictionCreateComponent,
    PlayCVPredictionCreateAdvancedComponent,
    PlayReplayCreateComponent,
    PlayPredictionViewComponent,
    PlayCVPredictionViewComponent,
    PlayReplayViewComponent,
    PlayContextComponent,
    CVPredictionTimeSpentSummaryComponent,
  ],
})
export class PlayModule {
  static forRoot(): ModuleWithProviders {
    return {
      ngModule: PlayModule,
      providers: [
        PredictionService,
        ReplayService,
        ReplayResolve,
        CVPredictionService,
        {
          provide: ASSET_BASE_ROUTE,
          useValue: playModuleAssetURLMap,
          multi: true,
        },
        {
          provide: LIBRARY_SECTIONS,
          deps: [PredictionService],
          useFactory: (service: PredictionService): LibrarySectionDefinition<IPrediction> => {
            return {
              service,
              assetType: IAsset.Type.PREDICTION,
              features: [Feature.PLAY_MODULE],
              icon: 'iconapp iconapp-models',
              inProjects: true,
              actions: {
              },
              baseRoute: ['/desk', 'play', 'predictions'],
              reloadOn: IEvent.Type.UPDATE_PREDICTION_LIST,
              statusesDefinition: config.prediction.status,
              completeStatus: IPredictionStatus.DONE,
              columns: [
              ],
              selectorColumns: [
              ],
              sharable: true,
              sidebarActions: [
              ],
              bulkOperations: [
              ],
            };
          },
          multi: true,
        },
        {
          provide: LIBRARY_SECTIONS,
          deps: [CVPredictionService],
          useFactory: (service: CVPredictionService): LibrarySectionDefinition<ICVPrediction> => {
            return {
              service,
              assetType: IAsset.Type.CV_PREDICTION,
              features: [Feature.PLAY_MODULE],
              icon: 'glyphicon glyphicon-blackboard',
              inProjects: true,
              actions: {
              },
              baseRoute: ['/desk', 'play', 'cv-predictions'],
              reloadOn: IEvent.Type.UPDATE_CV_PREDICTION_LIST,
              statusesDefinition: config.cvPrediction.status,
              completeStatus: ICVPredictionStatus.DONE,
              columns: [
              ],
              selectorColumns: [
              ],
              sharable: true,
              sidebarActions: [
              ],
              bulkOperations: [
              ],
            };
          },
          multi: true,
        },

      ],
    };
  }
}

// Features
declare module '../core/interfaces/feature-toggle.interface' {
  export const enum Feature {
    PLAY_MODULE = 'PLAY_MODULE',
  }
}
