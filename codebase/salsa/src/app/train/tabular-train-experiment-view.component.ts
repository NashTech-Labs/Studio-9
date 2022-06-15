import { Component, Input, OnInit } from '@angular/core';

import { merge as mergeObservables } from 'rxjs/observable/merge';
import { ISubscription } from 'rxjs/Subscription';

import config from '../config';
import { ifMocks, mocksMode } from '../core/core.mocks-only';
import { IAsset, TObjectId } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { ProcessService } from '../core/services/process.service';
import { IExperimentResultView } from '../experiments/experiment-result.component';
import { IExperimentFull } from '../experiments/experiment.interfaces';
import { ITable } from '../tables/table.interface';
import { TableService } from '../tables/table.service';
import { ReactiveLoader } from '../utils/reactive-loader';

import { IModelHelpers, ITabularModel, ITabularTrainPipeline, ITabularTrainResult } from './model.interface';
import { ModelService } from './model.service';

@Component({
  selector: 'model-experiment-view',
  template: `
    <div *ngIf="model">
      <div class="row">
        <div class="col-md-6">
          <app-fake-control
            *ngIf="inputTable"
            [label]="'Sampling Weight Column'"
            [value]="(experiment.pipeline.samplingWeightColumn | tableColumnDisplayName : inputTable) || 'None'"
          ></app-fake-control>
        </div>
      </div>
      <app-tabs
        [tabs]="TABS | map: _tabToLabelFn"
        [hiddenTabs]="hiddenTabs"
        [(active)]="activeTab"
      ></app-tabs>
      <!-- Tab panes -->
      <div class="flex-col" [adaptiveHeight]="{minHeight: 450, property: 'height'}">
        <!--input-->
        <table-view-embed
          [hidden]="activeTab !== TAB_INPUT"
          [id]="experiment.pipeline.input"
          [modelHelpers]="modelHelpers"
        ></table-view-embed>
        <!--hold out input-->
        <table-view-embed
          *ngIf="experiment.pipeline.holdOutInput"
          [hidden]="activeTab !== TAB_HOLD_OUT_INPUT"
          [id]="experiment.pipeline.holdOutInput"
          [modelHelpers]="modelHelpers"
        ></table-view-embed>
        <!--out of time input-->
        <table-view-embed
          *ngIf="experiment.pipeline.outOfTimeInput"
          [hidden]="activeTab !== TAB_OUT_OF_TIME_INPUT"
          [id]="experiment.pipeline.outOfTimeInput"
          [modelHelpers]="modelHelpers"
        ></table-view-embed>

        <ng-template [mocksOnly]="true">
          <!--advanced-->
          <div
            class="flex-static"
            *ngIf="experiment.pipeline.trainOptions"
            [hidden]="activeTab !== TAB_ADVANCED"
          >
            <div class="panel">
              <div class="panel-body">
                <model-view-advanced [trainOptions]="experiment.pipeline.trainOptions"></model-view-advanced>
              </div>
            </div>
          </div>
        </ng-template>

        <!--progress-->
        <div
          class="flex-col"
          *ngIf="model && model.status !== config.model.status.values.ACTIVE"
          [hidden]="activeTab < TAB_OUTPUT"
          [ngSwitch]="model.status"
        >
          <process-indicator
            *ngSwitchCase="config.model.status.values.TRAINING"
            [process]="modelProgresses[model.id]"
            [message]="'Training Model'"
          ></process-indicator>
          <process-indicator
            *ngSwitchCase="config.model.status.values.PREDICTING"
            [process]="modelProgresses[model.id]"
            [message]="'Generating model results'"
          ></process-indicator>
          <error-indicator
            *ngSwitchCase="config.model.status.values.CANCELLED"
            [caption]="'Cancelled'"
            [message]="'This model training has been cancelled'"
          ></error-indicator>
        </div>
        <!--training progress-->
        <ng-template [mocksOnly]="true">
          <model-train-summary
            *ngIf="activeTab === TAB_TRAINING_PROCESS"
            [model]="model"
            [process]="modelProgresses[model.id]"
          ></model-train-summary>
        </ng-template>
        <!--outputs-->
        <ng-template [ngIf]="model && model.status === config.model.status.values.ACTIVE">
          <table-view-embed
            [hidden]="activeTab !== TAB_OUTPUT"
            [id]="experiment.result.output"
            [modelHelpers]="modelHelpers"
          ></table-view-embed>
          <!--hold out output-->
          <table-view-embed
            *ngIf="experiment.result.holdOutOutput"
            [hidden]="activeTab !== TAB_HOLD_OUT_OUTPUT"
            [id]="experiment.result.holdOutOutput"
            [modelHelpers]="modelHelpers"
          ></table-view-embed>
          <!--out of time output-->
          <table-view-embed
            *ngIf="experiment.result.outOfTimeOutput"
            [hidden]="activeTab !== TAB_OUT_OF_TIME_OUTPUT"
            [id]="experiment.result.outOfTimeOutput"
            [modelHelpers]="modelHelpers"
          ></table-view-embed>

          <ng-template [mocksOnly]="true">
            <!--pipeline summary-->
            <div class="flex-static" [hidden]="activeTab !== TAB_MODEL_PIPELINE_SUMMARY"
              *ngIf="experiment.result.pipelineSummary">
              <div class="panel">
                <div class="panel-body">
                  <model-pipeline-summary [pipelineSummary]="experiment.result.pipelineSummary"></model-pipeline-summary>
                </div>
              </div>
            </div>
          </ng-template>

          <!-- model info -->
          <model-view-embed
            [hidden]="activeTab !== TAB_MODEL_INFO"
            [modelId]="model.id"
          ></model-view-embed>

          <!--summary-->
          <div
            class="flex-static"
            *ngIf="experiment.result.summary && activeTab === TAB_MODEL_SUMMARY"
          >
            <div class="panel">
              <div class="panel-body">
                <model-summary
                  [summary]="experiment.result.summary"
                  [holdOutSummary]="experiment.result.holdOutSummary"
                  [outOfTimeSummary]="experiment.result.outOfTimeSummary"
                  [trainOptions]="experiment.pipeline.trainOptions"
                  [validationThreshold]="experiment.pipeline.validationThreshold"
                ></model-summary>
              </div>
            </div>
          </div>
        </ng-template>
      </div>
    </div>
  `,
})
export class TabularTrainExperimentViewComponent
  implements OnInit, IExperimentResultView<ITabularTrainPipeline, ITabularTrainResult> {
  @Input() experiment: IExperimentFull<ITabularTrainPipeline, ITabularTrainResult>;

  model: ITabularModel;
  modelHelpers: IModelHelpers;

  readonly TAB_INPUT = 0;
  readonly TAB_HOLD_OUT_INPUT = 1;
  readonly TAB_OUT_OF_TIME_INPUT = 2;
  readonly TAB_ADVANCED = 3;
  readonly TAB_TRAINING_PROCESS = 4;
  readonly TAB_OUTPUT = 5;
  readonly TAB_HOLD_OUT_OUTPUT = 6;
  readonly TAB_OUT_OF_TIME_OUTPUT = 7;
  readonly TAB_MODEL_PIPELINE_SUMMARY = 8;
  readonly TAB_MODEL_INFO = 9;
  readonly TAB_MODEL_SUMMARY = 10;
  readonly TABS = [
    { label: 'Input Table', index: this.TAB_INPUT },
    { label: 'Hold-Out Table Input', index: this.TAB_HOLD_OUT_INPUT },
    { label: 'Out-Of-Time Table Input', index: this.TAB_OUT_OF_TIME_INPUT },
    { label: 'Advanced Options', index: this.TAB_ADVANCED },
    { label: 'Training process', index: this.TAB_TRAINING_PROCESS },
    { label: 'Output Table', index: this.TAB_OUTPUT },
    { label: 'Hold-Out Table Output', index: this.TAB_HOLD_OUT_OUTPUT },
    { label: 'Out-Of-Time Table Output', index: this.TAB_OUT_OF_TIME_OUTPUT },
    { label: 'Model Pipeline Summary', index: this.TAB_MODEL_PIPELINE_SUMMARY },
    { label: 'Model Info', index: this.TAB_MODEL_INFO },
    { label: 'Model Summary Statistics', index: this.TAB_MODEL_SUMMARY },
  ];
  readonly config = config;
  readonly ITabularModel = ITabularModel;
  readonly modelProgresses: { [id: string]: IProcess };

  inputTable: ITable;
  activeTab: number = ifMocks<number>(this.TAB_TRAINING_PROCESS, this.TAB_INPUT);

  hiddenTabs = [
    this.TAB_TRAINING_PROCESS,
    this.TAB_HOLD_OUT_INPUT,
    this.TAB_OUT_OF_TIME_INPUT,
    this.TAB_ADVANCED,
    this.TAB_HOLD_OUT_OUTPUT,
    this.TAB_OUT_OF_TIME_OUTPUT,
    this.TAB_MODEL_PIPELINE_SUMMARY,
    this.TAB_MODEL_SUMMARY,
  ];

  private processSubscription: ISubscription;
  private _loader: ReactiveLoader<[ITabularModel, ITable], TObjectId>;

  constructor(
    readonly modelService: ModelService,
    private processes: ProcessService,
    private tables: TableService,
  ) {
    this.modelProgresses = this.processes.data.targets[config.asset.aliasesPlural[config.asset.values.MODEL]];

    this._loader = new ReactiveLoader(id => {
      return this.modelService.get(id).flatMap(model => {
        return this.tables.get(this.experiment.pipeline.input).map((table): [ITabularModel, ITable] => [model, table]);
      });
    });

    this._loader.subscribe(([model, table]) => {
      this.setModel(model);
      this.inputTable = table;
    });
  }


  ngOnInit() {
    this.activeTab = this.TAB_INPUT;
    this.activeTab = ifMocks<number>(this.TAB_TRAINING_PROCESS, this.TAB_MODEL_INFO);
    this._loader.load(this.experiment.result.modelId);
  }

  setModel(model: ITabularModel) {
    this.model = model;
    this.modelHelpers = {
      responseColumn: this.experiment.pipeline.responseColumn,
      predictorColumns: this.experiment.pipeline.predictorColumns,
      probabilityColumns: this.experiment.result.probabilityColumns,
      predictedColumn: this.experiment.result.predictedColumn,
    };
    this.toggleTab(this.TAB_MODEL_SUMMARY, !!this.experiment.result.summary);
    this.toggleTab(this.TAB_MODEL_PIPELINE_SUMMARY, !!this.experiment.result.pipelineSummary);
    this.toggleTab(this.TAB_TRAINING_PROCESS, mocksMode);
    this.toggleTab(this.TAB_HOLD_OUT_INPUT, !!this.experiment.pipeline.holdOutInput);
    this.toggleTab(this.TAB_HOLD_OUT_OUTPUT, !!this.experiment.result.holdOutOutput);
    this.toggleTab(this.TAB_OUT_OF_TIME_INPUT, !!this.experiment.pipeline.outOfTimeInput);
    this.toggleTab(this.TAB_OUT_OF_TIME_OUTPUT, !!this.experiment.result.outOfTimeOutput);
    this.toggleTab(this.TAB_ADVANCED, !!this.experiment.pipeline.trainOptions);
    this.processSubscription && this.processSubscription.unsubscribe();
    switch (model.status) {
      case ITabularModel.Status.TRAINING:
      case ITabularModel.Status.PREDICTING:
        this.processSubscription = this.processes.subscribeByTarget(model.id, IAsset.Type.MODEL, () => {
          this._loader.load(model.id);
        });
        break;
      case ITabularModel.Status.ACTIVE:
        // evaluation might be still in progress
        const tablesToWatch: TObjectId[] = [this.experiment.result.holdOutOutput, this.experiment.result.outOfTimeOutput].filter(_ => !!_);
        this.processSubscription = mergeObservables(...tablesToWatch.map(tableId => this.tables.get(tableId)))
          .filter((table: ITable) => table.status === ITable.Status.SAVING)
          .flatMap(table => this.processes.getByTarget(table.id, IAsset.Type.TABLE))
          .flatMap(process => this.processes.observe(process))
          .subscribe(() => {
            this._loader.load(model.id);
          });
    }
  }

  toggleTab(index: number, show: boolean): void {
    if (show) {
      this.hiddenTabs = this.hiddenTabs.filter(_ => _ !== index);
    } else if (this.hiddenTabs.indexOf(index) === -1) {
      this.hiddenTabs.push(index);
    }
  }

  _tabToLabelFn(item) {
    return item.label;
  }
}

