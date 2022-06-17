import { ModuleWithProviders, NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';

import { CoreModule } from '../core/core.module';
import { EXPERIMENT_TYPES, ExperimentType, ExperimentTypeDefinition } from '../experiments/experiment.interfaces';

import { TestExperimentPipelineFormComponent } from './test-experiment-pipeline-form.component';
import { TestExperimentResultViewComponent } from './test-experiment-result-view.component';

const components = [
  TestExperimentPipelineFormComponent,
  TestExperimentResultViewComponent,
];

@NgModule({
  imports: [
    BrowserModule,
    FormsModule,
    ReactiveFormsModule,
    // deep cortex modules
    CoreModule,
  ],
  declarations: components,
  exports: components,
  entryComponents: components,
})
export class TestExperimentModule {
  static forRoot(): ModuleWithProviders {
    return {
      ngModule: TestExperimentModule,
      providers: [
        {
          provide: EXPERIMENT_TYPES,
          useFactory: (): ExperimentTypeDefinition => {
            return {
              type: ExperimentType.TestExperiment,
              name: 'Test Experiment',
              pipelineComponent: TestExperimentPipelineFormComponent,
              resultComponent: TestExperimentResultViewComponent,
              features: [],
            };
          },
          multi: true,
        },
      ],
    };
  }
}

// Experiment type
declare module '../experiments/experiment.interfaces' {
  export const enum ExperimentType {
    TestExperiment = 'TestExperiment',
  }
}
