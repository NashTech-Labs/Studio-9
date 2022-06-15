import { Component, Input } from '@angular/core';

import { IExperimentResultView } from '../experiments/experiment-result.component';
import { IExperimentFull } from '../experiments/experiment.interfaces';

import { ITestExperimentPipeline, ITestExperimentResult } from './test-experimentinterfaces';

@Component({
  selector: 'test-experiment-result-view',
  template: `
    <div class="tabpanel">
      <ul class="nav nav-tabs" role="tablist">
        <li role="presentation" [ngClass]="{'active': activeTab === 0}">
          <a (click)="activeTab = 0">Input</a>
        </li>
        <li role="presentation" [ngClass]="{'active': activeTab === 1}">
          <a (click)="activeTab = 1">Output</a>
        </li>
      </ul>
    </div>
    <div class="flex-static" [hidden]="activeTab !== 0">
      <div class="panel">
        <div class="panel-body">
          <dl>
            <dt>
              Test Field 1
            </dt>
            <dd>
              {{experiment.pipeline.testField1}}
            </dd>
            <ng-template [ngIf]="experiment.pipeline.testField2">
              <dt>
                Test Field 2
              </dt>
              <dd>
                {{experiment.pipeline.testField2}}
              </dd>
            </ng-template>
          </dl>
        </div>
      </div>
    </div>
    <div class="flex-static" [hidden]="activeTab !== 1">
      <div class="panel">
        <div class="panel-body">
          <dl>
            <dt>
              Test Field 1
            </dt>
            <dd>
              {{experiment.result.testField1}}
            </dd>
            <ng-template [ngIf]="experiment.result.testField2">
              <dt>
                Test Field 2
              </dt>
              <dd>
                {{experiment.result.testField2}}
              </dd>
            </ng-template>
          </dl>
        </div>
      </div>
    </div>
  `,
})
export class TestExperimentResultViewComponent
  implements IExperimentResultView<ITestExperimentPipeline, ITestExperimentResult> {
  @Input() experiment: IExperimentFull<ITestExperimentPipeline, ITestExperimentResult>;

  activeTab: number = 0;
}
