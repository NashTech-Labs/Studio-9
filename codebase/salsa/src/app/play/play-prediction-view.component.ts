import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { IAsset, TObjectId } from '../core/interfaces/common.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { ProcessService } from '../core/services/process.service';
import { AclService } from '../services/acl.service';
import { AppFormGroup } from '../utils/forms';
import { MiscUtils } from '../utils/misc';
import { ReactiveLoader } from '../utils/reactive-loader';

import { IPrediction } from './prediction.interface';
import { PredictionService } from './prediction.service';

@Component({
  selector: 'app-play-prediction',
  template: `
    <asset-operations [type]="config.asset.values.PREDICTION" [selectedItems]="[prediction]"
      (onDelete)="_onReplayDeleted()"></asset-operations>
    <app-spinner [visibility]="!prediction"></app-spinner>
    <div class="row brand-tab">
      <div class="col-md-6">
        <app-input [label]="'Prediction Name'" [control]="editForm.controls['name']"></app-input>
      </div>
      <div class="col-md-6">
        <div  class="pull-right">
          <button type="button"
            [disabled]="editForm.invalid || editForm.disabled || editForm.pristine"
            (click)="submitEditForm()"
            class="btn btn-md btn-apply"
          >Apply</button>
        </div>
      </div>
    </div>
    <div class="row">
      <div class="col-md-6">
        <app-description [control]="editForm.controls['description']"></app-description>
      </div>
    </div>
    <div *ngIf="prediction" [ngSwitch]="prediction.status">
      <!-- Error -->
      <error-indicator *ngSwitchCase="config.prediction.status.values.ERROR"
        [target]="'model'" [process]="progresses[prediction.id]"></error-indicator>
      <!-- In progress -->
      <process-indicator *ngSwitchCase="config.prediction.status.values.RUNNING"
        [process]="progresses[prediction.id]"></process-indicator>
      <!--Complete-->
      <div *ngSwitchCase="config.prediction.status.values.DONE">
        <div class="tabpanel">
          <!-- Nav tabs -->
          <ul class="nav nav-tabs" role="tablist">
            <li role="presentation" [ngClass]="{'active': activeTab === 0}">
              <a (click)="activeTab = 0">Model Info</a>
            </li>
            <li role="presentation" [ngClass]="{'active': activeTab === 1}">
              <a (click)="activeTab = 1">Input Table</a>
            </li>
            <li role="presentation" [ngClass]="{'active': activeTab === 2}">
              <a (click)="activeTab = 2">Output table</a>
            </li>
          </ul>
        </div>
        <!-- Tab panes -->
        <div class="flex-col" [adaptiveHeight]="{minHeight: 450}">
          <!-- Tab Model Summary Statistics -->
          <model-view-embed [hidden]="activeTab !== 0"
            [modelId]="prediction.modelId"
          ></model-view-embed>
          <!-- Tab Input -->
          <table-view-embed [hidden]="activeTab !== 1"
            [id]="prediction.input"
          ></table-view-embed>
          <!-- Tab Output -->
          <div class="flex-col" *ngIf="prediction.output" [hidden]="activeTab !== 2">
            <table-view-embed [id]="prediction.output"></table-view-embed>
          </div>
        </div>
      </div>
    </div>
  `,
})
export class PlayPredictionViewComponent implements OnInit, OnDestroy {
  config = config;
  prediction: IPrediction;
  activeTab: number = 2;

  readonly tabs = ['Input Table', 'Output Table', 'Model Summary'];
  readonly progresses = {};
  readonly _loader: ReactiveLoader<IPrediction, TObjectId>;
  readonly editForm = new AppFormGroup({
    name: new FormControl('', Validators.required),
    description: new FormControl(null),
  });

  private processSubscription: Subscription;
  private eventsSubscription: Subscription;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private predictions: PredictionService,
    private processService: ProcessService,
    private events: EventService,
    private acl: AclService,
  ) {
    this.progresses = this.processService.data.targets[config.asset.aliasesPlural[config.asset.values.PREDICTION]];

    this._loader = new ReactiveLoader(_ => this.predictions.get(_));

    this._loader.subscribe((prediction) => {
      this.refresh(prediction);
    });
  }

  ngOnInit() {
    this.route.params.forEach(params => {
      this.activeTab = 2;
      this._loader.load(params['predictionId']);
    });

    this.eventsSubscription = this.events.subscribe(event => {
      if (event.type === IEvent.Type.DELETE_PREDICTION && this.prediction.id === event.data.id) {
        this._onReplayDeleted();
      }
    });
  }

  ngOnDestroy() {
    this.processSubscription && this.processSubscription.unsubscribe();
    this.eventsSubscription && this.eventsSubscription.unsubscribe();
  }

  submitEditForm() {
    this.predictions.update(this.prediction.id, {
      name: this.editForm.value.name,
      description: this.editForm.value.description,
    }).subscribe(() => {
      this._loader.load(this.prediction.id);
    });
  }

  _onReplayDeleted() {
    this.router.navigate(['/desk', 'play']);
  }

  private refresh(prediction: IPrediction) {
    this.processSubscription && this.processSubscription.unsubscribe();

    this.prediction = prediction;

    if (this.prediction.status === config.prediction.status.values.RUNNING) {
      this.processSubscription = this.processService.subscribeByTarget(prediction.id, IAsset.Type.PREDICTION, () => {
        this._loader.load(prediction.id);
      });
    }

    const canUpdate = this.acl.canUpdatePrediction(this.prediction);
    MiscUtils.fillForm(this.editForm, this.prediction, !canUpdate);
  }
}
