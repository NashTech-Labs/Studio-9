import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';

import config from '../config';

import { ITabularModel } from './model.interface';
import { trainConfig } from './train.config';

@Component({
  selector: 'model-view-advanced',
  template: `
    <div>
      <i class="glyphicon" [ngClass]="trainOptions.variableImportance
        ? 'glyphicon-check'
        : 'glyphicon-unchecked'"
        aria-hidden="true"></i>
      Generate Variable Importance Summary
    </div>
    <div>
      <i class="glyphicon" [ngClass]="trainOptions.modelExplanation
        ? 'glyphicon-check'
        : 'glyphicon-unchecked'"
        aria-hidden="true"></i>
      Generate Model Explanation Columns
    </div>
    <h3>Pipeline Stages</h3>
    <div class="panel-group" role="tablist">
      <div *ngFor="let stage of _stages"
        class="panel"
        [ngClass]="_expandedStage === stage ? 'panel-primary' : 'panel-default'"
      >
        <div class="panel-heading" role="tab"
          [stickyHeader]="_expandedStage === stage">
          <h4 class="panel-title">
            <a role="button" (click)="_toggleStageCollapse(stage)">
              {{trainConfig.model.pipelineStage.labels[stage]}}
              <i class="glyphicon pull-right"
                [ngClass]="_expandedStage === stage ? 'glyphicon-chevron-up' : 'glyphicon-chevron-down'"
              ></i>
            </a>
          </h4>
        </div>

        <div
          role="tabpanel"
          class="panel-collapse collapse"
          [ngClass]="{'in': _expandedStage === stage}"
        >
          <ul *ngFor="let technique of trainOptions.techniques[stage]"
            class="list-group"
          >
            <li class="list-group-item">
              {{trainConfig.model.stageTechnique.labels[technique]}}
            </li>
            <ng-template [ngIf]="trainConfig.model.stageTechnique.params[technique].length > 0">
              <li *ngFor="let param of trainOptions.parameters[technique]"
                class="list-group-item"
                [ngSwitch]="!!param.values"
              >
                {{technique | apply: _paramTitle: param.name}}:
                <span *ngSwitchCase="true">
                  {{param.values | apply: _joinArray}}
                </span>
                <span *ngSwitchCase="false">
                  {{param.min | number: '1.0-5'}}
                  -
                  {{param.max | number: '1.0-5'}}
                </span>
              </li>
            </ng-template>
          </ul>
        </div>
      </div>
    </div>
  `,
})
export class ModelViewAdvancedComponent implements OnChanges {
  @Input() trainOptions: ITabularModel.TrainOptions;

  readonly config = config;
  readonly trainConfig = trainConfig;

  _expandedStage: ITabularModel.PipelineStage = null;

  _stages: ITabularModel.PipelineStage[] = [];

  constructor(
  ) {}

  ngOnChanges(changes: SimpleChanges) {
    this._stages = trainConfig.model.pipelineOrder
      .filter(stage => this.trainOptions.stages.indexOf(stage) >= 0);
  }

  _toggleStageCollapse(stage: ITabularModel.PipelineStage) {
    this._expandedStage = (this._expandedStage === stage)
      ? null
      : stage;
  }

  _paramTitle = (technique: ITabularModel.StageTechnique, name: string): string => {
    const paramDefinition = trainConfig.model.stageTechnique.params[technique].find(_ => _.name === name);
    return paramDefinition
      ? paramDefinition.title
      : name;
  };

  _joinArray = (array: string[]): string => {
    return array.join(', ');
  };
}

