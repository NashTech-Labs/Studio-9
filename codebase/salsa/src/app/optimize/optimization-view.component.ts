import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { ISubscription } from 'rxjs/Subscription';

import { IFlow } from '../compose/flow.interface';
import { FlowService } from '../compose/flow.service';
import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { IAsset, TObjectId } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { ProcessService } from '../core/services/process.service';
import { ITable } from '../tables/table.interface';
import { TableService } from '../tables/table.service';
import { TableColumnSelectOptionsPipe } from '../tables/tables.pipes';
import { ITabularModel } from '../train/model.interface';
import { ModelService } from '../train/model.service';

import { IOptimization } from './optimization.interface';
import { OptimizationService } from './optimization.service';

@Component({
  selector: 'app-optimization-view',
  template: `
    <asset-operations [type]="config.asset.values.OPTIMIZATION" [selectedItems]="[optimization]"
      (onDelete)="_onOptimizationDeleted()"></asset-operations>
    <div class="row brand-tab">
      <div class="col-md-6">
        <app-input [label]="'Optimization Name'" [value]="optimization?.name" [disabled]="true"></app-input>
      </div>
      <div class="col-md-6">
        <div class="pull-right">
          <button class="btn btn-apply" (click)="saveToLibrary()"
            [disabled]="optimization.status !== config.optimization.status.values.DONE">
            Save To Library
          </button>
        </div>
      </div>
    </div>
    <div class="row">
      <div class="col-md-6">
        <app-description [value]="optimization?.description" [disabled]="true"></app-description>
      </div>
    </div>
    <app-spinner [visibility]="loading"></app-spinner>
    <div *ngIf="!loading" class="brand-tab">
      <div class="row">
        <div class="col-md-6">
          <app-input [label]="'Optimization Type'" [disabled]="true"
            [value]="optimization.optimizationType"></app-input>
        </div>
        <div class="col-md-6">
          <library-selector
            [inputLabel]="'Source Model'"
            [available]="[config.asset.values.MODEL]"
            [value]="{id: optimization.modelId, entity: config.asset.values.MODEL}"
            [disabled]="true"
            [caption]="'Source Model'"></library-selector>
        </div>
        <div class="col-md-6">
          <app-input [label]="'Optimization Model Name'" [disabled]="true"
            [value]="optimization.outputModelName"></app-input>
        </div>
      </div>
      <process-indicator *ngIf="optimization.status === config.optimization.status.values.RUNNING"
        [process]="progresses[optimization.id]"
        [message]="'Optimizing'"></process-indicator>
      <ng-template [ngIf]="optimization.status === config.optimization.status.values.DONE">
        <div class="tabpanel">
          <!-- Nav tabs -->
          <ul class="nav nav-tabs" role="tablist">
            <li role="presentation" [ngClass]="{'active': activeTab === -1}">
              <a (click)="activeTab = -1">Optimization Parameters</a>
            </li>
            <li role="presentation" [ngClass]="{'active': activeTab === 0}">
              <a (click)="activeTab = 0">Input Table</a>
            </li>
            <li role="presentation" [ngClass]="{'active': activeTab === 1}">
              <a (click)="activeTab = 1">Output table</a>
            </li>
            <li *ngIf="optimization.summary"
              role="presentation" [ngClass]="{'active': activeTab === 2}">
              <a (click)="activeTab = 2">Model Summary Statistics</a>
            </li>
          </ul>
        </div>
        <!-- Tab panes -->
        <div class="flex-col" [adaptiveHeight]="{minHeight: 450}">
          <div [hidden]="activeTab !== -1">
            <div class="row">
              <div class="col-md-6">
                <app-input [disabled]="true" [label]="'Goal'" [value]="optimization.objectives[0].goal"></app-input>
              </div>
              <ng-template [ngIf]="optimization.optimizationType === config.optimization.type.labels.PREDICTOR_TUNING">
                <div class="col-md-6">
                  <app-input [label]="'Metric'"
                    [value]="optimization.objectives[0].metric"
                    [disabled]="true"
                  ></app-input>
                </div>
              </ng-template>
              <ng-template [ngIf]="optimization.optimizationType === config.optimization.type.labels.OBJECTIVE_FUNCTION">
                <div class="col-md-6">
                  <library-selector
                    [inputLabel]="'Objective Flow'"
                    [available]="[config.asset.values.FLOW]"
                    [disabled]="true"
                    [value]="{id: optimization.objectiveFlowId, entity: config.asset.values.FLOW}"
                  ></library-selector>
                </div>
                <div class="col-md-6">
                  <app-input [label]="'Metric'"
                    [value]="optimization.objectives[0].columnName"
                    [disabled]="true"
                  ></app-input>
                </div>
              </ng-template>
            </div>
            <ng-template [ngIf]="constraintsTable">
              <div class="row">
                <div class="col-md-12">
                  Constraints
                </div>
              </div>
              <div class="row pt5">
                <div class="col-md-6">
                  <library-selector
                    [inputLabel]="'Constraint Flow'"
                    [available]="[config.asset.values.FLOW]"
                    [disabled]="true"
                    [value]="{id: optimization.constraintFlowId, entity: config.asset.values.FLOW}"
                    [caption]="'Source Model'"></library-selector>
                </div>
              </div>
              <div class="row"
                *ngFor="let constraint of optimization['optimizationConstraints']">
                <div class="col-md-2">
                  <app-input [label]="'Column'"
                    [value]="constraint.columnName | tableColumnDisplayName : constraintsTable"
                    [disabled]="true"></app-input>
                </div>
                <div class="col-md-2">
                  <app-input [value]="filterConfig.operator.labels[constraint.relationalOperator]"
                    [disabled]="true"
                  ></app-input>
                </div>
                <div class="col-md-2">
                  <app-input [label]="'Value'"
                    [value]="constraint.value"
                    [disabled]="true"
                  ></app-input>
                </div>
              </div>
            </ng-template>
          </div>
          <table-view-embed
            [hidden]="activeTab !== 0"
            [id]="optimization.input"
            [modelHelpers]="model"
          ></table-view-embed>
          <table-view-embed
            [hidden]="activeTab !== 1"
            [id]="optimization.outputTable"
            [modelHelpers]="model"
          ></table-view-embed>
          <div class="flex-static" [hidden]="activeTab !== 2"
            *ngIf="optimization.summary">
            <div class="panel">
              <div class="panel-body">
                <model-summary
                  [summary]="optimization.summary"
                  [holdOutSummary]="optimization.holdOutSummary"
                  [outOfTimeSummary]="optimization.outOfTimeSummary"
                ></model-summary>
              </div>
            </div>
          </div>
        </div>
      </ng-template>
    </div>
  `,
})
export class OptimizationViewComponent implements OnInit, OnDestroy {
  readonly config = config;
  readonly filterConfig = config.flowstep.option.filter;
  loading: boolean = false;
  optimization: IOptimization;
  flow: IFlow;
  activeTab: number = 2;
  inputs: TObjectId[] = [];
  model: ITabularModel;
  progresses: {[id: string]: IProcess};
  constraintsTable: ITable;
  objectiveColumnOptions: AppSelectOptionData[] = [];
  inputColumnOptions: AppSelectOptionData[] = [];
  private processSubscription: ISubscription;
  private eventsSubscription: ISubscription;
  private tableColumnSelectOptionsPipe = new TableColumnSelectOptionsPipe();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private optimizationService: OptimizationService,
    private models: ModelService,
    private processes: ProcessService,
    private flows: FlowService,
    private tables: TableService,
    private events: EventService,
  ) {
    this.progresses = this.processes.data.targets[config.asset.aliasesPlural[config.asset.values.OPTIMIZATION]];
  }

  ngOnInit() {
    this.route.data.forEach((data: { optimization: IOptimization }) => {
      this.processSubscription && this.processSubscription.unsubscribe();

      this.optimization = data.optimization;
      if (this.optimization.constraintFlowId) {
        this.flows.get(this.optimization.constraintFlowId).subscribe(flow => {
          const tableId: TObjectId = flow.steps[flow.steps.length - 1].output;
          this.tables.get(tableId).subscribe((table) => {
            this.constraintsTable = table;
            this.getColumns(table);
          });
        });
      }
      if (this.optimization.optimizationType === config.optimization.type.values.OBJECTIVE_FUNCTION) {
        this.flows.get(this.optimization.objectiveFlowId).subscribe(flow => {
          const tableId: TObjectId = flow.steps[flow.steps.length - 1].output;
          this.tables.get(tableId).subscribe((table) => {
            this.objectiveColumnOptions = this.tableColumnSelectOptionsPipe.transform(table.columns);
          });
        });
      }
      this._loadOptimization(data.optimization);
      if (data.optimization.status === config.optimization.status.values.RUNNING) {
        this.processSubscription = this.processes.subscribeByTarget(data.optimization.id, IAsset.Type.OPTIMIZATION, () => {
          this.optimizationService.get(this.optimization.id).subscribe((optimization: IOptimization) => {
            this.optimization = optimization;
            this.activeTab = 1;
            this._loadOptimization(data.optimization, true);
          });
        });
      }
    });

    this.eventsSubscription = this.events.subscribe(event => {
      if (event.type === IEvent.Type.DELETE_OPTIMIZATION && this.optimization.id === event.data.id) {
        this._onOptimizationDeleted();
      }
    });
  }

  ngOnDestroy() {
    this.processSubscription && this.processSubscription.unsubscribe();
    this.eventsSubscription && this.eventsSubscription.unsubscribe();
  }

  saveToLibrary() {
    /*this.optimizationService.save(this.optimization.id).subscribe(_ => {
     this._loadOptimization(this.optimization);
     });*/
  }

  getColumns(table: ITable) {
    this.inputColumnOptions = this.tableColumnSelectOptionsPipe.transform(table.columns);
  }

  _onOptimizationDeleted() {
    this.router.navigate(['/desk', 'optimization']);
  }

  private _loadOptimization(optimization: IOptimization, silent?: boolean): void {
    if (!silent) {
      this.loading = true;
    }

    this.models.get(optimization.outputModelId).subscribe((model: ITabularModel) => {
      this.model = model;
      this.loading = false;
    });
  }
}
