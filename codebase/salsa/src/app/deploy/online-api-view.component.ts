import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { ISubscription } from 'rxjs/Subscription';

import config from '../config';
import { IAsset, TObjectId } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { ProcessService } from '../core/services/process.service';
import { ITable } from '../tables/table.interface';
import { ITabularModel } from '../train/model.interface';
import { ModelService } from '../train/model.service';
import { ActivityObserver } from '../utils/activity-observer';
import { AppFormGroup } from '../utils/forms';
import { MiscUtils } from '../utils/misc';
import { ReactiveLoader } from '../utils/reactive-loader';

import { IOnlineAPI } from './online-api.interface';
import { OnlineAPIService } from './online-api.service';

@Component({
  selector: 'deploy-online-api-view',
  template: `
    <asset-operations [type]="config.asset.values.ONLINE_API" [selectedItems]="[item]"
      (onDelete)="onDelete()"></asset-operations>
    <app-spinner [visibility]="_itemLoader.active | async"></app-spinner>
    <ng-template [ngIf]="item && _itemLoader.loaded">
      <div class="row">
        <div class="col-md-6">
          <app-input [label]="'Online Job Name'" [control]="form.controls['name']"></app-input>
        </div>
        <div class="col-md-4">
          <app-check
            [label]="'Enabled'"
            [value]="true"
            [control]="form.controls.enabled"
          ></app-check>
        </div>
        <div class="col-md-2">
          <div class="pull-right">
            <button class="btn btn-md btn-apply" (click)="_savingObserver.observe(submit())"
              [disabled]="form.invalid || (_savingObserver.active | async)">
              Update
            </button>
          </div>
        </div>
      </div>
      <div class="row">
        <div class="col-md-6">
          <app-description [control]="form.controls['description']"></app-description>
        </div>
      </div>
      <div class="row">
        <div class="col-md-6">
          <library-selector
            [inputLabel]="'Target'"
            [value]="{id: item.target.id, entity: item.target.type}"
            [disabled]="true"
          ></library-selector>
        </div>
      </div>
      <process-indicator
        *ngIf="item.status == '${IOnlineAPI.Status.PREPARING}'"
        [process]="itemProcess"
        [message]="'API is preparing'"
      ></process-indicator>
      <div class="panel">
        <div class="panel-body" [ngSwitch]="item.target.type">
          <dl class="dl-horizontal" *ngSwitchCase="'${IAsset.Type.PIPELINE}'">
            <dt>API URL</dt>
            <dd>https://dev.studio9.ai/api-call/{{item.id}}/run</dd>
            <dt>Sample call</dt>
            <dd>
              <div class="row">
                <div class="col-sm-6">
                  POST <strong>{{config.api.base}}api-call/{{item.id}}</strong>:
                  <pre>[[ payload is dependant on pipeline ]]</pre>
                </div>
                <div class="col-sm-6">
                  Response:
                  <pre>[[ response is dependant on pipeline ]]</pre>
                </div>
              </div>
            </dd>
          </dl>
          <dl class="dl-horizontal" *ngSwitchCase="'${IAsset.Type.MODEL}'">
            <dt>API URL</dt>
            <dd>https://dev.studio9.ai/api-call/{{item.id}}/predict</dd>
            <dt>Sample call</dt>
            <dd>
              <app-spinner [visibility]="_modelLoader.active | async"></app-spinner>
              <ng-template [ngIf]="model">
                <div class="row">
                  <div class="col-sm-6">
                    POST <strong>{{config.api.base}}api-call/{{item.id}}/predict</strong>:
                    <pre>{{ model | apply: _prepareSamplePredictRequest | json }}</pre>
                  </div>
                  <div class="col-sm-6">
                    Response:
                    <pre>{{ model | apply: _prepareSamplePredictResponse | json }}</pre>
                  </div>
                </div>
              </ng-template>
            </dd>
          </dl>
        </div>
      </div>
    </ng-template>
  `,
})
export class OnlineAPIViewComponent implements OnInit, OnDestroy {
  readonly config = config;
  readonly _savingObserver = new ActivityObserver();
  readonly form: AppFormGroup<{
    name: FormControl;
    description: FormControl;
    enabled: FormControl,
  }>;
  readonly _itemLoader: ReactiveLoader<IOnlineAPI, TObjectId>;
  readonly _modelLoader: ReactiveLoader<ITabularModel, TObjectId>;
  protected item: IOnlineAPI;
  protected model: ITabularModel;
  protected itemProcess: IProcess;

  private routeSubscription: ISubscription;
  private eventsSubscription: ISubscription;
  private processSubscription: ISubscription;

  constructor(
    private service: OnlineAPIService,
    private modelService: ModelService,
    private route: ActivatedRoute,
    private router: Router,
    private events: EventService,
    private _processService: ProcessService,
  ) {
    this.form = new AppFormGroup({
      name: new FormControl(null, Validators.required),
      description: new FormControl(null),
      enabled: new FormControl(false),
    });

    this._itemLoader = new ReactiveLoader((jobId) => this.service.get(jobId));
    this._modelLoader = new ReactiveLoader((modelId) => this.modelService.get(modelId));

    this._itemLoader.subscribe((api: IOnlineAPI) => {
      this.item = api;
      MiscUtils.fillForm(this.form, {
        name: api.name,
        enabled: api.status === IOnlineAPI.Status.ACTIVE,
      });

      this.processSubscription && this.processSubscription.unsubscribe();
      this.processSubscription = this.service.getActiveProcess(api)
        .do(process => {
          this.itemProcess = process; // processService will update this process object status
        })
        .filter(_ => !!_)
        .flatMap(process => {
          return this._processService.observe(process);
        })
        .subscribe(() => {
          this._itemLoader.load(api.id);
        });

      if (api.target.type === IAsset.Type.MODEL) {
        this._modelLoader.load(api.target.id);
      }
    });

    this.eventsSubscription = this.events.subscribe(event => {
      if (event.type === IEvent.Type.DELETE_ONLINE_API && this.item.id === event.data.id) {
        this.onDelete();
      }
    });

    this._modelLoader.subscribe((model: ITabularModel) => {
      this.model = model;
    });
  }

  ngOnInit() {
    this.routeSubscription = this.route.params.subscribe(params => {
      this._itemLoader.load(params['itemId']);
    });
  }

  ngOnDestroy() {
    this.routeSubscription && this.routeSubscription.unsubscribe();
    this.eventsSubscription && this.eventsSubscription.unsubscribe();
  }

  submit() {
    const observable = this.service.update(this.item.id, this.form.value);

    observable.subscribe(() => this._itemLoader.load(this.item.id));

    return observable;
  }

  onDelete() {
    this.router.navigate(['/desk', 'deploy', 'create']);
  }

  protected _prepareSamplePredictRequest = (model: ITabularModel): any => {
    const dataSample = {};
    model.predictorColumns.forEach(predictor => {
      switch (predictor.dataType) {
        case ITable.ColumnDataType.INTEGER:
        case ITable.ColumnDataType.LONG:
          dataSample[predictor.name] = 1;
          break;
        case ITable.ColumnDataType.DOUBLE:
          dataSample[predictor.name] = 0.01;
          break;
        case ITable.ColumnDataType.BOOLEAN:
          dataSample[predictor.name] = true;
          break;
        case ITable.ColumnDataType.STRING:
          dataSample[predictor.name] = 'foo';
          break;
        case ITable.ColumnDataType.TIMESTAMP:
          dataSample[predictor.name] = '1970-01-01T00:00:00';
          break;
        default:
          throw new Error('I\'m a teapot');
      }
    });
    return {
      data: [dataSample],
    };
  };

  protected _prepareSamplePredictResponse = (model: ITabularModel): any => {
    const dataSample = {};
    switch (model.responseColumn.dataType) {
      case ITable.ColumnDataType.INTEGER:
      case ITable.ColumnDataType.LONG:
        dataSample[model.responseColumn.name] = 1;
        break;
      case ITable.ColumnDataType.DOUBLE:
        dataSample[model.responseColumn.name] = 0.01;
        break;
      case ITable.ColumnDataType.BOOLEAN:
        dataSample[model.responseColumn.name] = true;
        break;
      case ITable.ColumnDataType.STRING:
        dataSample[model.responseColumn.name] = 'foo';
        break;
      case ITable.ColumnDataType.TIMESTAMP:
        dataSample[model.responseColumn.name] = '1970-01-01T00:00:00';
        break;
      default:
        throw new Error('I\'m a teapot');
    }
    return {
      predictions: [dataSample],
    };
  };
}
