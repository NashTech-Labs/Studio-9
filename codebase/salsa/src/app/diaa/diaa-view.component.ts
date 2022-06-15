import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import 'rxjs/add/observable/forkJoin';
import 'rxjs/add/observable/of';
import 'rxjs/add/operator/mergeMap';
import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { IAsset, TObjectId } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { ProcessService } from '../core/services/process.service';
import { ExperimentService } from '../experiments/experiment.service';
import { ITable } from '../tables/table.interface';
import { TableService } from '../tables/table.service';
import {
  ITabularModel,
  ITabularTrainModel,
  ITabularTrainPipeline,
  ITabularTrainResult,
} from '../train/model.interface';
import { ModelService } from '../train/model.service';
import { ReactiveLoader } from '../utils/reactive-loader';

import { DIAAViewAnalysisComponent } from './diaa-view-analysis.component';
import { diaaConfig } from './diaa.config';
import { IDIAA } from './diaa.interface';
import { DIAAService } from './diaa.service';


@Component({
  selector: 'diaa-view',
  template: `
    <asset-operations [type]="config.asset.values.DIAA" [selectedItems]="[diaa]"
      (onDelete)="_onDIAADeleted()"></asset-operations>
    <div class="row brand-tab">
      <div class="col-md-6">
        <app-input [label]="'Analysis Name'" [value]="diaa?.name" [disabled]="true"></app-input>
      </div>
      <div class="col-md-6">
        <div class="pull-right">
          <button *ngIf="_diaaStage === 'check'"
            class="btn btn-apply" (click)="runAnalysis()"
            [disabled]="_isRunning">
            Run Analysis
          </button>
          <button *ngIf="_diaaStage === 'analysis'"
            class="btn" (click)="_diaaStage = 'check'; activeTab = 2"
            [disabled]="_isRunning">
            Change Objective/Constraints
          </button>
          <button *ngIf="_diaaStage === 'analysis'"
            class="btn btn-apply" (click)="saveToLibrary()"
            [disabled]="_isRunning">
            Save To Library
          </button>
        </div>
      </div>
    </div>
    <div class="row">
      <div class="col-md-6">
        <app-description [value]="diaa?.description" [disabled]="true"></app-description>
      </div>
    </div>

    <div *mocksOnly="true" class="row">
      <div class="col-md-6">
        <app-fake-control *ngIf="table" [label]="'Sampling Weight Column'"
          [value]="(inputModel.pipeline?.samplingWeightColumn | tableColumnDisplayName : table) || 'None'"
        ></app-fake-control>
      </div>
    </div>

    <app-spinner [visibility]="_loader.active | async"></app-spinner>
    <div *ngIf="_loader.loaded" class="brand-tab">
      <app-tabs [tabs]="_diaaStage === 'analysis'
        ? ['AIR/SMD Specification', 'Original Model Summary', 'Alternative Model Summary', 'DIA Analysis Summary']
        : ['AIR/SMD Specification', 'Original Model Summary', 'DIA Analysis Specification']
      " [(active)]="activeTab"></app-tabs>
      <div class="panel" *ngIf="activeTab === 0">
        <div class="panel-body">
          <diaa-specification
            [diaa]="diaa"
          ></diaa-specification>
        </div>
      </div>

      <div class="panel" *ngIf="activeTab === 1">
        <div class="panel-body">
          <diaa-model-summary *ngIf="!_isRunning || (_diaaStage === 'analysis')"
            [diaa]="diaa"
            [summary]="diaa.summary"
          ></diaa-model-summary>
          <model-summary
            [summary]="inputModel.result?.summary"
            [holdOutSummary]="inputModel.result?.holdOutSummary"
            [outOfTimeSummary]="inputModel.result?.outOfTimeSummary"
            [trainOptions]="inputModel.pipeline?.trainOptions"
            [validationThreshold]="inputModel.pipeline?.validationThreshold"
          ></model-summary>
        </div>
      </div>

      <ng-template [ngIf]="!_isRunning && _diaaStage === 'check'">
        <diaa-view-analysis [hidden]="activeTab !== 2"
          #analysisForm
          [diaa]="diaa"
          [model]="inputModel"
        ></diaa-view-analysis>
      </ng-template>

      <ng-template [ngIf]="!_isRunning && _diaaStage === 'analysis'">
        <div class="panel" *ngIf="activeTab === 2">
          <div class="panel-body">
            <diaa-model-summary *ngIf="(_diaaStage === 'analysis') || !_isRunning"
              [diaa]="diaa"
              [summary]="diaa.altSummary"
            ></diaa-model-summary>
            <model-summary
              [summary]="outputModel.result?.summary"
              [holdOutSummary]="outputModel.result?.holdOutSummary"
              [outOfTimeSummary]="outputModel.result?.outOfTimeSummary"
              [trainOptions]="outputModel.pipeline?.trainOptions"
              [validationThreshold]="outputModel.pipeline?.validationThreshold"
            ></model-summary>
          </div>
        </div>
        <div class="panel" *ngIf="activeTab === 3">
          <div class="panel-body">
            <diaa-summary
              [diaa]="diaa"
              [originalModel]="inputModel"
              [model]="outputModel"
            ></diaa-summary>
          </div>
        </div>
      </ng-template>


      <ng-template [ngIf]="_isRunning">
        <div class="tab-pane"
          [ngClass]="{'active': activeTab >= 2}"
          [ngSwitch]="diaa.status"
        >
          <process-indicator *ngSwitchCase="diaaConfig.status.values.CHECKING"
            [process]="diaaProgresses[diaa.id]"
            [target]="'DI Check'"
          ></process-indicator>
          <process-indicator *ngSwitchCase="diaaConfig.status.values.RUNNING"
            [process]="diaaProgresses[diaa.id]"
            [target]="'DIA Analysis'"
          ></process-indicator>
          <error-indicator *ngSwitchCase="diaaConfig.status.values.ERROR"
            [process]="diaaProgresses[diaa.id]"
            [target]="'DIA Analysis'"
          ></error-indicator>
        </div>
      </ng-template>
    </div>
  `,
})
export class DIAAViewComponent implements OnInit, OnDestroy {
  readonly config = config;

  readonly diaaConfig = diaaConfig;
  diaa: IDIAA;

  inputModel: ITabularTrainModel;
  outputModel: ITabularTrainModel;
  table: ITable;
  activeTab: number = 2;
  readonly diaaProgresses: { [id: string]: IProcess };

  _diaaStage: 'check' | 'analysis' = 'check';
  _isRunning: boolean = false;

  readonly _loader: ReactiveLoader<[IDIAA, ITabularTrainModel, ITabularTrainModel], TObjectId>;
  readonly _tableLoader: ReactiveLoader<ITable, TObjectId>;

  @ViewChild('analysisForm') private _analysisForm: DIAAViewAnalysisComponent;
  private processSubscription: Subscription;
  private eventsSubscription: Subscription;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: DIAAService,
    private models: ModelService,
    private processes: ProcessService,
    private tables: TableService,
    private events: EventService,
    private experimentService: ExperimentService,
  ) {
    this.diaaProgresses = this.processes.data.targets[config.asset.aliasesPlural[config.asset.values.DIAA]];

    this._loader = new ReactiveLoader<[IDIAA, ITabularTrainModel, ITabularTrainModel], TObjectId>(id => {
      return this.service.get(id).flatMap((diaa: IDIAA) => {
        return Observable.forkJoin(
          Observable.of(diaa),
          this._getTabularTrainModel(diaa.modelId),
          (diaa.status === IDIAA.Status.DONE)
            ? this._getTabularTrainModel(diaa.outputModelId)
            : Observable.of(null),
        );
      });
    });
    this._tableLoader = new ReactiveLoader(id => this.tables.get(id));

    this._loader.subscribe(([diaa, inputModel, outputModel]) => {
      this.diaa = diaa;
      this.inputModel = inputModel;
      this.outputModel = outputModel;

      this._tableLoader.load(inputModel.pipeline.input);
      const checkComplete = diaa.status === IDIAA.Status.RUNNING || diaa.status === IDIAA.Status.DONE;

      this._diaaStage = checkComplete
        ? 'analysis'
        : 'check';

      this.activeTab = checkComplete ? 3 : 2;

      this._isRunning = diaa.status === IDIAA.Status.RUNNING || diaa.status === IDIAA.Status.CHECKING;
      if (this._isRunning) {
        this.processSubscription && this.processSubscription.unsubscribe();
        this.processSubscription = this.processes.subscribeByTarget(diaa.id, IAsset.Type.DIAA, () => {
          this._loader.load(diaa.id);
        });
      }
    });

    this._tableLoader.subscribe(table => {
      this.table = table;
    });
  }

  ngOnInit() {
    this.route.params.forEach(params => {
      this._loader.load(params['diaaId']);
    });

    this.eventsSubscription = this.events.subscribe(event => {
      if (event.type === IEvent.Type.DELETE_DIAA && this.diaa.id === event.data.id) {
        this._onDIAADeleted();
      }
    });
  }

  ngOnDestroy() {
    this.eventsSubscription && this.eventsSubscription.unsubscribe();
    this.processSubscription && this.processSubscription.unsubscribe();
  }

  runAnalysis() {
    this._analysisForm.submit().subscribe(diaa => {
      this._loader.load(diaa.id);
    });
  }

  saveToLibrary() {
  }

  _onDIAADeleted() {
    this.router.navigate(['/desk', 'diaa']);
  }

  private _getTabularTrainModel(modelId: TObjectId): Observable<ITabularTrainModel> {
    return this.models.get(modelId).flatMap((model: ITabularModel) => {
      return model.experimentId
        ? this.experimentService.get(model.experimentId).flatMap(experiment => {
          return Observable.of({
            ...model,
            pipeline: experiment.pipeline as ITabularTrainPipeline,
            result: experiment.result as ITabularTrainResult,
          });
        })
        : Observable.of(null);
    });
  }
}
