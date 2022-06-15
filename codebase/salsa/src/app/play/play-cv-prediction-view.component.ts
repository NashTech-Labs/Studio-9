import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import 'rxjs/add/observable/forkJoin';
import 'rxjs/add/observable/of';
import 'rxjs/add/operator/map';
import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import { IAlbum } from '../albums/album.interface';
import { AlbumService } from '../albums/album.service';
import config from '../config';
import { IAsset, TObjectId } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { ProcessService } from '../core/services/process.service';
import { AclService } from '../services/acl.service';
import { ICVModel } from '../train/cv-model.interface';
import { CVModelService } from '../train/cv-model.service';
import { AppFormGroup } from '../utils/forms';
import { MiscUtils } from '../utils/misc';
import { ReactiveLoader } from '../utils/reactive-loader';

import { ICVPrediction } from './cv-prediction.interface';
import { CVPredictionService } from './cv-prediction.service';

@Component({
  selector: 'app-play-cv-prediction',
  template: `
    <asset-operations [type]="config.asset.values.CV_PREDICTION" [selectedItems]="[prediction]"
      (onDelete)="_onPredictionDeleted()"></asset-operations>
    <app-spinner [visibility]="!prediction"></app-spinner>
    <div *ngIf="prediction" class="row brand-tab">
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
    <div *ngIf="prediction" class="row">
      <div class="col-md-6">
        <app-description [control]="editForm.controls['description']"></app-description>
      </div>
    </div>
    <div *ngIf="_loader.loaded && prediction" [ngSwitch]="prediction.status">
      <!-- Error -->
      <error-indicator *ngSwitchCase="config.cvPrediction.status.values.ERROR"
        [target]="'prediction'" [process]="progresses[prediction.id]"></error-indicator>
      <!-- In progress -->
      <process-indicator *ngSwitchCase="config.cvPrediction.status.values.RUNNING"
        [process]="progresses[prediction.id]"></process-indicator>
      <!--Complete-->
      <div *ngSwitchCase="config.cvPrediction.status.values.DONE">
        <div class="tabpanel">
          <!-- Nav tabs -->
          <ul class="nav nav-tabs" role="tablist">
            <li role="presentation" [ngClass]="{'active': activeTab === 0}">
               <a (click)="activeTab = 0">Model Info</a>
            </li>
            <li role="presentation" [ngClass]="{'active': activeTab === 1}">
              <a (click)="activeTab = 1">Input Album</a>
            </li>
            <li role="presentation" [ngClass]="{'active': activeTab === 2}">
              <a (click)="activeTab = 2">Output Album</a>
            </li>
            <li *ngIf="prediction.summary" role="presentation"
              [ngClass]="{'active': activeTab === 3}">
              <a (click)="activeTab = 3">Evaluation Summary</a>
            </li>
            <li *ngIf="prediction.evaluationTimeSpentSummary || prediction.predictionTimeSpentSummary"
              role="presentation" [ngClass]="{'active': activeTab === 4}">
              <a (click)="activeTab = 4">Time-spent summary</a>
            </li>
            <li
              *ngIf="prediction.probabilityPredictionTableId"
              role="presentation"
              [ngClass]="{'active': activeTab === 4}"
            >
              <a (click)="activeTab = 4">Probability Distribution</a>
            </li>
          </ul>
        </div>
        <!-- Tab panes -->
        <div class="flex-col" [adaptiveHeight]="{minHeight: 450}">
           <!-- Tab Model Summary Statistics -->
           <div role="tabpanel" class="tab-pane" [hidden]="activeTab !== 0">
             <cv-model-view-embed
               [model]="predictionModel"
             ></cv-model-view-embed>
           </div>
           <!-- Tab Input -->
           <div class="flex-col" *ngIf="prediction.input" [hidden]="activeTab !== 1">
             <album-view-embed [albumId]="prediction.input"></album-view-embed>
           </div>
           <!-- Tab Output -->
           <div class="flex-col" *ngIf="prediction.output" [hidden]="activeTab !== 2">
             <album-view-embed [albumId]="prediction.output"></album-view-embed>
           </div>

          <div class="flex-col" *ngIf="prediction.summary" [hidden]="activeTab !== 3">
            <div class="panel">
              <div class="panel-body">
                <cv-model-summary
                  [summary]="prediction?.summary"
                  [modelType]="predictionModel?.modelType"
                  [actualAlbumId]="prediction?.input"
                ></cv-model-summary>
              </div>
            </div>
          </div>
          <div class="flex-col" [hidden]="activeTab !== 4"
            *ngIf="prediction.predictionTimeSpentSummary || prediction.evaluationTimeSpentSummary">
            <div class="panel">
              <div class="panel-body">
                <cv-prediction-time-spent-summary
                  [prediction]="prediction">
                </cv-prediction-time-spent-summary>
              </div>
            </div>
          </div>

          <div class="flex-col" *ngIf="prediction.probabilityPredictionTableId" [hidden]="activeTab !== 4">
            <table-view-embed
              [id]="prediction.probabilityPredictionTableId"
              [showDownload]="true"
            ></table-view-embed>
          </div>
        </div>
      </div>
    </div>
  `,
})
export class PlayCVPredictionViewComponent implements OnInit, OnDestroy {
  config = config;
  prediction: ICVPrediction;
  predictionModel: ICVModel | null;
  predictionAlbum: IAlbum | null;
  activeTab: number = 2;
  readonly progresses: {[id: string]: IProcess} = {};
  readonly _loader: ReactiveLoader<[ICVPrediction, ICVModel, IAlbum], TObjectId>;
  readonly editForm = new AppFormGroup({
    name: new FormControl('', Validators.required),
    description: new FormControl(null),
  });

  private processSubscription: Subscription;
  private eventsSubscription: Subscription;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private models: CVModelService,
    private albums: AlbumService,
    private predictions: CVPredictionService,
    private processService: ProcessService,
    private events: EventService,
    private acl: AclService,
  ) {
    this.progresses = this.processService.data.targets[config.asset.aliasesPlural[config.asset.values.CV_PREDICTION]];

    this._loader = new ReactiveLoader(_ => {
      return this.predictions.get(_)
        .flatMap((prediction: ICVPrediction): Observable<[ICVPrediction, ICVModel, IAlbum]> => {
          if (prediction.status === config.cvPrediction.status.values.DONE) {
            return Observable.forkJoin(
              this.models.get(prediction.modelId),
              this.albums.get(prediction.output),
            ).map(([model, album]): [ICVPrediction, ICVModel, IAlbum] => [prediction, model, album]);
          } else {
            return Observable.of(<[ICVPrediction, ICVModel, IAlbum]> [
              prediction,
              null,
              null,
            ]);
          }
        });
    });

    this._loader.subscribe(([prediction, model, album]) => {
      this.refresh(prediction, model, album);
    });
  }

  ngOnInit() {
    this.route.params.forEach(params => {
      this.activeTab = 2;
      this._loader.load(params['predictionId']);
    });

    this.eventsSubscription = this.events.subscribe(event => {
      if (event.type === IEvent.Type.DELETE_CV_PREDICTION && this.prediction.id === event.data.id) {
        this._onPredictionDeleted();
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

  _onPredictionDeleted() {
    this.router.navigate(['/desk', 'play']);
  }

  private refresh(prediction: ICVPrediction, model: ICVModel, album: IAlbum) {
    this.processSubscription && this.processSubscription.unsubscribe();

    this.prediction = prediction;
    this.predictionModel = model;
    this.predictionAlbum = album;

    if (this.prediction.status === config.cvPrediction.status.values.RUNNING) {
      this.processSubscription = this.processService
        .subscribeByTarget(prediction.id, IAsset.Type.CV_PREDICTION, () => {
          this._loader.load(prediction.id);
        });
    }

    const canUpdate = this.acl.canUpdateCVPrediction(this.prediction);
    MiscUtils.fillForm(this.editForm, this.prediction, !canUpdate);
  }
}
