import { Component, Input, OnDestroy, OnInit } from '@angular/core';

import { Subscription } from 'rxjs/Subscription';

import { IAlbum } from '../albums/album.interface';
import { IAsset, IAssetReference, TObjectId } from '../core/interfaces/common.interface';
import { AssetReferenceParameterDefinition } from '../core/interfaces/params.interface';
import { IExperimentResultView } from '../experiments/experiment-result.component';
import { IExperimentFull } from '../experiments/experiment.interfaces';
import { ReactiveLoader } from '../utils/reactive-loader';

import { IGenericExperiment, Pipeline, PipelineOperator } from './pipeline.interfaces';
import { PipelineService } from './pipeline.service';

@Component({
  selector: 'pipeline-experiment-result-view',
  template: `
    <div class="tabpanel">
      <ul class="nav nav-tabs" role="tablist">
        <li role="presentation" [ngClass]="{'active': activeTab === 0}">
          <a (click)="activeTab = 0">Pipeline</a>
        </li>
        <li role="presentation" [ngClass]="{'active': activeTab === 1}">
          <a (click)="activeTab = 1">Results</a>
        </li>
      </ul>
    </div>
    <div class="flex-static" [hidden]="activeTab !== 0">
      <div class="panel">
        <div class="panel-body">
          <app-spinner [visibility]="_loader.active | async"></app-spinner>
          <app-pipeline-canvas
            *ngIf="_loader.loaded"
            [steps]="experiment.pipeline.steps"
            [availableOperators]="availableOperators"
            [pipelineEditor]="false"
          ></app-pipeline-canvas>
        </div>
      </div>
    </div>
    <div class="flex-static" [hidden]="activeTab !== 1">
      <div class="panel" *ngIf="!!experiment.result">
        <div class="panel-body">
          <p *ngIf="!experiment.result.steps.length">No results to display</p>

          <div
            *ngFor="let data of experiment.result.steps | apply: getStepData: experiment.pipeline.steps"
            class="panel"
            [ngClass]="{
              'panel-danger': isErrorResult(data.result),
              'panel-primary': !isErrorResult(data.result)
            }"
          >
            <div class="panel-heading">
              <strong>
                {{data.step.operator | apply: getOperatorName: availableOperators}}
              </strong>
            </div>

            <div class="panel-body">
              <ng-container *ngIf="isErrorResult(data.result)">
                <div class="row">
                  <div class="col col-md-8 col-xs-12">
                    <pre class="pre-scrollable auto-hide-scrollbar" style="border: none">{{data.result.errorMessage}}</pre>
                  </div>

                  <div class="col col-md-4 col-xs-12">
                    <p><strong>Assets:</strong></p>
                    <asset-list
                      [assetReferences]="data.result.assets"
                    ></asset-list>
                  </div>
                </div>
              </ng-container>

              <ng-container *ngIf="!isErrorResult(data.result)">
                <div class="row">
                  <div class="col col-md-4 col-md-push-8 col-xs-12">
                    <ng-container  *ngIf="data.result.assets?.length">
                      <p><strong>Assets generated:</strong></p>
                      <asset-list
                        [assetReferences]="data.result.assets"
                      ></asset-list>
                    </ng-container>
                    <ng-container *ngVar="(data.step | apply: getStepSelectedAssets: experiment.pipeline.assets : availableOperators) as inputAssets">
                      <ng-container *ngIf="inputAssets?.length">
                        <p><strong>Assets selected:</strong></p>
                        <asset-list
                          [assetReferences]="inputAssets"
                        ></asset-list>
                      </ng-container>
                    </ng-container>
                  </div>

                  <div class="col col-md-8 col-md-pull-4 col-xs-12">
                    <p>
                      <strong>Execution Time:</strong>
                      {{data.result.executionTime | secondsToTime}}
                    </p>

                    <ng-container *ngIf="(data.result.outputValues | apply: objectKeys).length">
                      <p><strong>Output values:</strong></p>
                      <dl class="row" *ngFor="let key of data.result.outputValues | apply: objectKeys">
                        <dd class="col-md-10">{{data.result.outputValues[key]}}</dd>
                      </dl>
                    </ng-container>

                    <ng-container *ngIf=" data.result.summaries?.length">
                      <p><strong>Summary:</strong></p>
                      <ng-container *ngFor="let summary of data.result.summaries">
                        <ng-container *ngIf="isSummaryTypeSimple(summary)">
                          <dl class="dl-horizontal">
                            <ng-container *ngFor="let key of summary.values | apply: objectKeys">
                              <dt title="{{key}}">{{key}}</dt>
                              <dd>{{summary.values[key]}}</dd>
                            </ng-container>
                          </dl>
                        </ng-container>
                      </ng-container>
                    </ng-container>
                  </div>
                </div>

                <ng-container *ngFor="let summary of data.result.summaries">
                  <cv-confusion-matrix
                    *ngIf="isSummaryTypeConfusionMatrix(summary)"
                    [labelMode]="'${IAlbum.LabelMode.LOCALIZATION}'"
                    [labels]="summary.labels"
                    [confusionMatrix]="summary.rows"
                  ></cv-confusion-matrix>
                </ng-container>
              </ng-container>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
})
export class PipelineExperimentResultViewComponent
  implements IExperimentResultView<IGenericExperiment.Pipeline, IGenericExperiment.Result>, OnInit, OnDestroy {
  @Input() experiment: IExperimentFull<IGenericExperiment.Pipeline, IGenericExperiment.Result>;

  activeTab: number = 0;
  availableOperators: PipelineOperator[] = [];

  protected readonly _loader: ReactiveLoader<PipelineOperator[], TObjectId>;
  private _subscriptions: Subscription[] = [];

  constructor(
    private _pipelineService: PipelineService,
  ) {
    this._loader = new ReactiveLoader(() => {
      return this._pipelineService.listAllOperators();
    });
    const loaderSubscription = this._loader.subscribe(this._onDataLoaded.bind(this));
    this._subscriptions.push(loaderSubscription);
  }

  _onDataLoaded(operators: PipelineOperator[]): void {
    this.availableOperators = operators;
  }

  ngOnInit(): void {
    this._loader.load();
  }

  ngOnDestroy(): void {
    this._subscriptions.forEach(_ => _.unsubscribe());
  }

  getStepData = function(
    resultSteps: IGenericExperiment.StepResultSuccess[],
    steps: Pipeline.Step[],
  ): { step: Pipeline.Step, result: IGenericExperiment.StepResult }[] {
    return resultSteps.map(result => {
      const step = steps.find(_ => result.stepId === _.id);
      if (!step) {
        throw new Error('Can\'t find step. Please contact developers');
      }

      return {
        step: step,
        result: result,
      };
    }).reverse();
  };

  getOperatorName = function (operatorId: TObjectId, operators: PipelineOperator[]): string {
    const operator = operators.find(_ => operatorId === _.id);
    return operator ? operator.name : `Unknown operator with ID "${operatorId}`;
  };

  isSummaryTypeSimple(
    summary: IGenericExperiment.OperatorApplicationSummary,
  ): summary is IGenericExperiment.SimpleSummary {
    return summary.type === IGenericExperiment.SummaryType.SIMPLE;
  }

  isSummaryTypeConfusionMatrix(
    summary: IGenericExperiment.OperatorApplicationSummary,
  ): summary is IGenericExperiment.ConfusionMatrixSummary {
    return summary.type === IGenericExperiment.SummaryType.CONFUSION_MATRIX;
  }

  getStepSelectedAssets(
    step: Pipeline.Step,
    pipelineAssets: IAssetReference[],
    availableOperators: PipelineOperator[],
  ): IAssetReference[] {
    const stepOperator = availableOperators.find(operator => operator.id === step.operator);
    if (!stepOperator || !stepOperator.params) {
      return [];
    }
    const assetParameters = stepOperator.params.filter(_ => _.type === 'assetReference') as AssetReferenceParameterDefinition[];
    if (!assetParameters.length) {
      return [];
    }
    const stepAssets: [IAsset.Type, string][] = assetParameters.reduce((acc, parameter) => {
      if (parameter.name in step.params) {
        acc.push([parameter.assetType, step.params[parameter.name]]);
      }
      return acc;
    }, []);
    if (!stepAssets.length) {
      return [];
    }
    return pipelineAssets.filter(assetRef => stepAssets.some(_ => _[0] === assetRef.type && _[1] === assetRef.id));
  }

  isErrorResult(result: IGenericExperiment.StepResultSuccess) {
    return 'errorMessage' in result;
  }

  readonly objectKeys = function(o: any): string[] {
    return Object.keys(o).sort();
  };
}
