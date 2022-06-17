import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';

import config from '../config';

import { ITabularModel } from './model.interface';
import { trainConfig } from './train.config';

@Component({
  selector: 'model-pipeline-summary',
  template: `
    <div *ngIf="!pipelineSummary" class="text-center">
      No pipeline summary
    </div>
    <ng-template [ngIf]="pipelineSummary">
      <dl *ngFor="let stage of _stageSummaries" class="dl-horizontal train-dl-summary">
        <dt>Stage</dt>
        <dd>{{trainConfig.model.pipelineStage.labels[stage.stage]}}</dd>
        <dt>Technique</dt>
        <dd>{{trainConfig.model.stageTechnique.labels[stage.technique]}}</dd>
        <ng-template [ngIf]="trainConfig.model.stageTechnique.params[stage.technique].length > 0">
          <dt>Parameters</dt>
          <dd>
            <table class="table table-bordered table-sm">
              <thead>
              <tr><th style="width: 30%">Parameter</th><th>Value</th></tr>
              </thead>
              <tbody>
              <tr *ngFor="let param of stage.parameters"
                [ngSwitch]="!!param.stringValue">
                <td>{{_paramTitle(stage.technique, param.name)}}</td>
                <td *ngSwitchCase="true">{{param.stringValue}}</td>
                <td *ngSwitchCase="false">{{param.value | number: '1.0-5'}}</td>
              </tr>
              </tbody>
            </table>
          </dd>
        </ng-template>
      </dl>
    </ng-template>
  `,
})
export class ModelPipelineSummaryComponent implements OnChanges {
  @Input() pipelineSummary: ITabularModel.PipelineSummary;

  readonly config = config;
  readonly trainConfig = trainConfig;

  _stageSummaries: ITabularModel.PipelineSummaryStage[] = [];

  constructor(
  ) {}

  ngOnChanges(changes: SimpleChanges) {
    // they are ordered
    this._stageSummaries = trainConfig.model.pipelineOrder
      .map(stage => this.pipelineSummary.stages.find(_ => _.stage === stage))
      .filter(_ => !!_);
  }

  private _paramTitle = (technique: ITabularModel.StageTechnique, name: string): string => {
    const paramDefinition = trainConfig.model.stageTechnique.params[technique].find(_ => _.name === name);
    return paramDefinition
      ? paramDefinition.title
      : name;
  };
}

