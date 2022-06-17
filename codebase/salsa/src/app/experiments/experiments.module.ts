import { ModuleWithProviders, NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';

import config from '../config';
import { CoreModule } from '../core/core.module';
import { ASSET_BASE_ROUTE, IAsset } from '../core/interfaces/common.interface';
import { Feature } from '../core/interfaces/feature-toggle.interface';
import { IEvent } from '../core/services/event.service';
import { LIBRARY_SECTIONS, LibrarySectionDefinition } from '../library/library.interface';

import { ExperimentCreateComponent } from './experiment-create.component';
import { ExperimentPipelineComponent } from './experiment-pipeline.component';
import { ExperimentResultComponent } from './experiment-result.component';
import { ExperimentViewComponent } from './experiment-view.component';
import { EXPERIMENT_TYPES, ExperimentTypeDefinition, IExperiment } from './experiment.interfaces';
import { ExperimentService } from './experiment.service';
import { ExperimentsContextComponent } from './experiments-context.component';
import { experimentsAssetURLMap } from './experiments.routes';

@NgModule({
  imports: [
    RouterModule,
    BrowserModule,
    FormsModule,
    ReactiveFormsModule,
    // deep cortex modules
    CoreModule,
  ],
  declarations: [
    ExperimentsContextComponent,
    ExperimentCreateComponent,
    ExperimentViewComponent,
    ExperimentPipelineComponent,
    ExperimentResultComponent,
  ],
  exports: [
    ExperimentCreateComponent,
    ExperimentViewComponent,
  ],
})
export class ExperimentsModule {
  static forRoot(): ModuleWithProviders {
    return {
      ngModule: ExperimentsModule,
      providers: [
        ExperimentService,
        {
          provide: ASSET_BASE_ROUTE,
          useValue: experimentsAssetURLMap,
          multi: true,
        },
        {
          provide: LIBRARY_SECTIONS,
          deps: [ExperimentService, EXPERIMENT_TYPES],
          useFactory: (
            service: ExperimentService,
            typeDefinitions: ExperimentTypeDefinition[],
          ): LibrarySectionDefinition<IExperiment> => {
            const getExperimentTypeName = (e: IExperiment) =>
              typeDefinitions.filter(_ => _.type === e.type).map(_ => _.name)[0] || e.type;

            return {
              service,
              assetType: IAsset.Type.EXPERIMENT,
              icon: 'glyphicon glyphicon-education',
              inProjects: true,
              actions: {},
              baseRoute: ['/desk', 'experiments'],
              reloadOn: IEvent.Type.UPDATE_EXPERIMENT_LIST,
              statusesDefinition: config.experiments.status,
              completeStatus: IExperiment.Status.COMPLETED,
              columns: [
                {name: 'Type', get: getExperimentTypeName, style: 'width: 12%'},
              ],
              selectorColumns: [
                {name: 'Type', get: getExperimentTypeName, style: 'width: 12%'},
              ],
              features: [Feature.EXPERIMENTS_MODULE],
              sharable: false,
              sidebarActions: [
                {
                  caption: 'Create Experiment',
                  navigateTo: ['/desk', 'experiments', 'create'],
                },
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
    EXPERIMENTS_MODULE = 'EXPERIMENTS_MODULE',
  }
}

// Experiment type
declare module '../experiments/experiment.interfaces' {
  export const enum ExperimentType {
    GenericExperiment = 'GenericExperiment',
  }
}
