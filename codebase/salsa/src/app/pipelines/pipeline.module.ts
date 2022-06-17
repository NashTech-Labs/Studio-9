import { ModuleWithProviders, NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';

import * as _ from 'lodash';

import { maybe } from '../../lib/maybe';
import { AlbumsModule } from '../albums/albums.module';
import config from '../config';
import { CoreUIModule } from '../core-ui/core-ui.module';
import { CoreModule } from '../core/core.module';
import { IAsset } from '../core/interfaces/common.interface';
import { IEvent } from '../core/services/event.service';
import { EXPERIMENT_TYPES, ExperimentType, ExperimentTypeDefinition } from '../experiments/experiment.interfaces';
import { ExperimentService } from '../experiments/experiment.service';
import { LIBRARY_SECTIONS, LibrarySectionDefinition } from '../library/library.interface';
import { TablesModule } from '../tables/tables.module';
import { TrainModule } from '../train/train.module';

import { CanvasContextComponent } from './canvas-context.component';
import { CanvasComponent } from './canvas.component';
import { OperatorInfoModalComponent } from './operator-info-modal.component';
import { OperatorParamsComponent } from './operator-params.component';
import { PipelineOperatorPositioningService } from './operator-positioning.service';
import { PipelineContextComponent } from './pipeline-context.component';
import { PipelineCreateComponent } from './pipeline-create.component';
import { PipelineExperimentResultViewComponent } from './pipeline-experiment-result-view.component';
import { PipelineRunModalComponent } from './pipeline-run-modal.component';
import { PipelineComponent } from './pipeline.component';
import { IGenericExperiment, Pipeline } from './pipeline.interfaces';
import { PipelineService } from './pipeline.service';

@NgModule({
  imports: [
    RouterModule,
    BrowserModule,
    FormsModule,
    ReactiveFormsModule,
    // deep cortex modules
    CoreModule,
    CoreUIModule,
    TablesModule,
    AlbumsModule,
    TrainModule,
  ],
  declarations: [
    PipelineComponent,
    CanvasComponent,
    PipelineCreateComponent,
    PipelineContextComponent,
    CanvasContextComponent,
    PipelineExperimentResultViewComponent,
    PipelineRunModalComponent,
    OperatorInfoModalComponent,
    OperatorParamsComponent,
  ],
  entryComponents: [
    PipelineExperimentResultViewComponent,
    PipelineRunModalComponent,
  ],
  exports: [],
})
export class PipelineModule {
  static forRoot(): ModuleWithProviders {
    return {
      ngModule: PipelineModule,
      providers: [
        PipelineService,
        PipelineOperatorPositioningService,
        {
          provide: EXPERIMENT_TYPES,
          useFactory: (): ExperimentTypeDefinition => {
            return {
              type: ExperimentType.GenericExperiment,
              name: 'Generic Experiment',
              pipelineComponent: null,
              resultComponent: PipelineExperimentResultViewComponent,
              resultComponentHandlesErrors: true,
              features: [],
            };
          },
          multi: true,
        },
        {
          provide: LIBRARY_SECTIONS,
          deps: [PipelineService],
          useFactory: (service: PipelineService): LibrarySectionDefinition<Pipeline> => {
            return {
              service,
              assetType: IAsset.Type.PIPELINE,
              icon: 'glyphicon glyphicon-random',
              inProjects: true,
              actions: {
              },
              baseRoute: ['/desk', 'pipelines'],
              reloadOn: IEvent.Type.UPDATE_PIPELINE_LIST,
              statusesDefinition: config.pipeline.status,
              completeStatus: null,
              columns: [
              ],
              selectorColumns: [
              ],
              sharable: false,
              sidebarActions: [
                {
                  caption: 'Create Pipeline',
                  navigateTo: ['/desk', 'pipelines'],
                },
              ],
              bulkOperations: [
                {
                  name: 'Run',
                  iconClass: 'glyphicon glyphicon-play',
                  isAvailable: (items) => items.length === 1,
                  modalClass: PipelineRunModalComponent,
                },
              ],
            };
          },
          multi: true,
        },
      ],
    };
  }
}

ExperimentService.registerChildAssetsExtractor(ExperimentType.GenericExperiment, experiment => {
  const pipeline = <IGenericExperiment.Pipeline> experiment.pipeline;
  const result = maybe(<IGenericExperiment.Result> experiment.result);

  return [
    ...(pipeline.assets || []),
    ...(result.map(res => _.flatten(res.steps.map(step => {
      return [...step.assets];
    }))).get() || []),
  ];
});
