import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { forkJoin } from 'rxjs/observable/forkJoin';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { TObjectId } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { ProcessService } from '../core/services/process.service';
import { AclService } from '../services/acl.service';
import { MiscUtils } from '../utils/misc';
import { ReactiveLoader } from '../utils/reactive-loader';

import { ICVArchitecture, ICVClassifier, ICVDetector } from './cv-architecture.interfaces';
import { CVArchitectureService } from './cv-architecture.service';
import { CVModelType, ICVModel } from './cv-model.interface';
import { CVModelService } from './cv-model.service';
import { trainConfig } from './train.config';

@Component({
  selector: 'cv-model-view',
  template: `
    <asset-operations [type]="config.asset.values.CV_MODEL" [selectedItems]="[model]"
      (onDelete)="_onModelDeleted()"></asset-operations>
    <app-spinner [visibility]="!model || !architectures"></app-spinner>
    <form *ngIf="model && architectures" [formGroup]="modelEditForm" class="brand-tab">
      <div class="row">
        <div class="col-md-6">
          <app-input [label]="'Model Name'" [control]="modelEditForm.controls['name']"></app-input>
        </div>
        <div class="col-md-6">
          <div class="btn-group pull-right" role="group">
            <button type="button"
              [disabled]="modelEditForm.invalid || modelEditForm.disabled || modelEditForm.pristine"
              (click)="submit()"
              class="btn btn-md btn-apply"
            >Update</button>
          </div>
        </div>
      </div>
      <div class="row">
        <div class="col-md-6">
          <app-description [control]="modelEditForm.controls['description']"></app-description>
        </div>
      </div>
      <div class="panel">
        <div class="panel-body">
          <dl>
            <ng-template [ngIf]="model.experimentId">
              <dt>Experiment</dt>
              <dd><a [routerLink]="['/desk','experiments', model.experimentId]">go to</a></dd>
            </ng-template>
            <ng-container *ngIf="model.modelType | apply: asTLModelType as modelTypeTL">
              <dt>Model Type:</dt>
              <dd>{{trainConfig.cvModel.tlType.labels[modelTypeTL.tlType]}}</dd>
              <dt>UTLP:</dt>
              <dd>{{modelTypeTL.architecture | apply: getArchitectureName: architectures}}</dd>
              <ng-template [ngIf]="modelTypeTL.tlType === '${CVModelType.TLType.LOCALIZATION}'">
                <dt>Detector Type:</dt>
                <dd>{{modelTypeTL.detectorType | apply: getDetectorName: detectors}}</dd>
              </ng-template>
              <ng-template [ngIf]="modelTypeTL.tlType === '${CVModelType.TLType.CLASSIFICATION}'">
                <dt>Classifier Type:</dt>
                <dd>{{modelTypeTL.classifierType | apply: getClassifierName: classifiers}}</dd>
              </ng-template>
            </ng-container>

            <ng-container *ngIf="model.modelType | apply: asCustomModelType as modelTypeCustom">
              <dt>Model Type:</dt>
              <dd *ngVar="modelTypeCustom.classReference as classReference">
                {{trainConfig.cvModel.modelType.labels[modelTypeCustom.type]}}:
                {{classReference.moduleName}}/{{classReference.className}}<span *ngIf="classReference?.packageId"> ({{classReference.packageId}})</span>
              </dd>
            </ng-container>
          </dl>
        </div>
      </div>
      <div class="tabpanel">
        <!-- Nav tabs -->
        <ul class="nav nav-tabs" role="tablist">
        </ul>
      </div>
      <!-- Tab panes -->
      <div class="flex-col" [adaptiveHeight]="{minHeight: 450}"
        *ngIf="model"
        [ngSwitch]="model.status === config.cvModel.status.values.ACTIVE"
      >
        <div *ngSwitchCase="false"
          [hidden]="activeTab < 1"
          [ngSwitch]="model.status"
        >
          <process-indicator
            *ngSwitchCase="config.cvModel.status.values.SAVING"
            [process]="modelProcess"
            [message]="'Saving cv-model'"
          ></process-indicator>
          <process-indicator *ngSwitchCase="config.cvModel.status.values.TRAINING"
            [process]="modelProcess"
            [message]="'Training cv-model'"
          ></process-indicator>
          <error-indicator *ngSwitchCase="config.cvModel.status.values.ERROR"
            [target]="'model'" [process]="modelProcess"
          ></error-indicator>
          <error-indicator *ngSwitchCase="config.cvModel.status.values.CANCELLED"
            [caption]="'Cancelled'" [message]="'This model training has been cancelled'"
          ></error-indicator>
        </div>
      </div>
    </form>
  `,
})
export class CVModelViewComponent implements OnInit, OnDestroy {
  readonly config = config;
  readonly trainConfig = trainConfig;
  readonly IModelType = CVModelType;
  modelEditForm: FormGroup;
  activeTab: number = 2;
  modelProcess: IProcess;
  model: ICVModel;

  readonly _modelLoader: ReactiveLoader<ICVModel, TObjectId>;
  readonly _architecturesLoader: ReactiveLoader<[ICVArchitecture[], ICVClassifier[], ICVDetector[]], any>;

  architectures: ICVArchitecture[];
  classifiers: ICVClassifier[];
  detectors: ICVDetector[];

  private eventSubscription: Subscription;
  private processSubscription: Subscription;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private models: CVModelService,
    private events: EventService,
    private processes: ProcessService,
    private acl: AclService,
    cvArchitectureService: CVArchitectureService,
  ) {
    this.modelEditForm = new FormGroup({
      name: new FormControl(null, Validators.required),
      description: new FormControl(null),
    });
    this.eventSubscription = this.events.subscribe((event) => {
      if (event.type === IEvent.Type.DELETE_CV_MODEL && this.model.id === event.data.id) {
        this._onModelDeleted();
      }
      if (event.type === IEvent.Type.UPDATE_CV_MODEL && this.model.id === event.data.id) {
        this._modelLoader.load(this.model.id);
      }
    });

    // init loaders
    this._modelLoader = new ReactiveLoader(id => this.models.get(id));
    this._modelLoader.subscribe(model => {
      this.setModel(model);
    });

    this._architecturesLoader = new ReactiveLoader(() => forkJoin(
      cvArchitectureService.listArchitectures(),
      cvArchitectureService.listClassifiers(),
      cvArchitectureService.listDetectors(),
    ));

    this._architecturesLoader.subscribe(([architectures, classifiers, detectors]) => {
      this.architectures = architectures;
      this.classifiers = classifiers;
      this.detectors = detectors;
    });
  }

  ngOnInit() {
    this.route.params.forEach(params => {
      this._modelLoader.load(params['itemId']);
    });

    this._architecturesLoader.load();
  }

  setModel(model: ICVModel) {
    this.model = model;
    this.fillModelsForm(this.modelEditForm, model);
    this.processSubscription && this.processSubscription.unsubscribe();
    this.processSubscription = this.models.getActiveProcess(model)
      .do(process => {
        this.modelProcess = process; // processService will update this process object status
      })
      .filter(_ => !!_)
      .flatMap(process => {
        return this.processes.observe(process);
      })
      .subscribe(() => {
        this._modelLoader.load(model.id);
      });
  }

  ngOnDestroy() {
    this.processSubscription && this.processSubscription.unsubscribe();
    this.eventSubscription.unsubscribe();
  }

  submit() {
    this.models.update(this.model.id, {
      name: this.modelEditForm.value.name,
      description: this.modelEditForm.value.description,
    }).subscribe(() => {
      this._modelLoader.load(this.model.id);
    });
  }

  asTLModelType = function(modelType: CVModelType): CVModelType.TL {
    return modelType.type === CVModelType.Type.TL && modelType;
  };

  asCustomModelType = function(modelType: CVModelType): CVModelType.Custom {
    return modelType.type === CVModelType.Type.CUSTOM && modelType;
  };

  getArchitectureName = function(id: string, architectures: ICVArchitecture[]) {
    const item = architectures.find(_ => _.id === id);
    return item ? item.name : id;
  };

  getClassifierName = function(id: string, classifiers: ICVClassifier[]) {
    const item = classifiers.find(_ => _.id === id);
    return item ? item.name : id;
  };

  getDetectorName = function(id: string, detectors: ICVDetector[]) {
    const item = detectors.find(_ => _.id === id);
    return item ? item.name : id;
  };

  _onModelDeleted() {
    this.router.navigate(['/desk', 'train']);
  }

  private fillModelsForm(form: FormGroup, model?: any | ICVModel) {
    const canUpdate = this.acl.canUpdateCVModel(model);
    form.reset({
      name: null,
      description: null,
    });
    MiscUtils.fillForm(form, model, !canUpdate);
  }
}

