import { Component, Input, OnInit } from '@angular/core';

import config from '../config';
import { TObjectId } from '../core/interfaces/common.interface';
import { ReactiveLoader } from '../utils/reactive-loader';

import { ICVArchitecture, ICVClassifier, ICVDetector } from './cv-architecture.interfaces';
import { ICVModel } from './cv-model.interface';
import { CVModelService } from './cv-model.service';
import { ICVTLTrainStepParams, ICVTLTrainStepResult } from './cvtl-train.interfaces';
import { trainConfig } from './train.config';

@Component({
  selector: 'cv-model-experiment-step-view',
  template: `
    <app-spinner [visibility]="!model"></app-spinner>
    <div *ngIf="model">
      <cv-model-view-embed
        [model]="model"
      ></cv-model-view-embed>
      <div class="tabpanel">
        <!-- Nav tabs -->
        <ul class="nav nav-tabs" role="tablist">
          <ng-template [ngIf]="pipeline.input && result.output">
            <li role="presentation" [ngClass]="{'active': activeTab === 0}">
              <a (click)="activeTab = 0">Input Album</a>
            </li>
            <li role="presentation" [ngClass]="{'active': activeTab === 1}">
              <a (click)="activeTab = 1">Output Album</a>
            </li>
          </ng-template>
          <li role="presentation" [ngClass]="{'active': activeTab === 2}">
            <a (click)="activeTab = 2">Model Summary Statistics</a>
          </li>
          <ng-template [ngIf]="pipeline.testInput && result.testOutput">
            <li role="presentation" [ngClass]="{'active': activeTab === 3}">
              <a (click)="activeTab = 3">Test Input</a>
            </li>
            <li role="presentation" [ngClass]="{'active': activeTab === 4}">
              <a (click)="activeTab = 4">Test Output</a>
            </li>
            <li role="presentation" [ngClass]="{'active': activeTab === 5}">
              <a (click)="activeTab = 5">Test Summary Statistics</a>
            </li>
          </ng-template>
          <ng-template [ngIf]="result.probabilityPredictionTableId">
            <li role="presentation" [ngClass]="{'active': activeTab === 6}">
              <a (click)="activeTab = 6">Probability Distribution</a>
            </li>
          </ng-template>
          <ng-template [ngIf]="result.testProbabilityPredictionTableId">
            <li role="presentation" [ngClass]="{'active': activeTab === 7}">
              <a (click)="activeTab = 7">Test Probability Distribution</a>
            </li>
          </ng-template>
          <ng-template [ngIf]="result.augmentationSummary">
            <li role="presentation" [ngClass]="{'active': activeTab === 8}">
              <a (click)="activeTab = 8">Augmentation Summary</a>
            </li>
          </ng-template>
          <ng-template [ngIf]="result.augmentedSampleAlbum">
            <li role="presentation" [ngClass]="{'active': activeTab === 9}">
              <a (click)="activeTab = 9">Augmented Sample Album</a>
            </li>
          </ng-template>
          <ng-template [ngIf]="result.trainTimeSpentSummary">
            <li role="presentation" [ngClass]="{'active': activeTab === 10}">
              <a (click)="activeTab = 10">Time-spent Summary</a>
            </li>
          </ng-template>
        </ul>
      </div>
      <!-- Tab panes -->
      <div class="flex-col" [adaptiveHeight]="{minHeight: 450}" *ngIf="model">
        <album-view-embed
          *ngIf="pipeline.input"
          [hidden]="activeTab !== 0"
          [albumId]="pipeline.input"
        ></album-view-embed>

        <album-view-embed
          *ngIf="result.output"
          [hidden]="activeTab !== 1"
          [albumId]="result.output"
        ></album-view-embed>

        <div class="flex-static"
          [hidden]="activeTab !== 2"
        >
          <div class="panel">
            <div class="panel-body">
              <cv-model-summary
                [summary]="result.summary"
                [modelType]="model.modelType"
              ></cv-model-summary>
            </div>
          </div>
        </div>

        <ng-template [ngIf]="pipeline.testInput">
          <album-view-embed
            [hidden]="activeTab !== 3"
            [albumId]="pipeline.testInput"
          ></album-view-embed>
          <album-view-embed
            [hidden]="activeTab !== 4"
            [albumId]="result.testOutput"
          ></album-view-embed>
          <div class="flex-static"
            *ngIf="result.testSummary"
            [hidden]="activeTab !== 5"
          >
            <div class="panel">
              <div class="panel-body">
                <cv-model-summary
                  [summary]="result.testSummary"
                  [modelType]="model.modelType"
                ></cv-model-summary>
              </div>
            </div>
          </div>
        </ng-template>

        <table-view-embed
          *ngIf="result.probabilityPredictionTableId"
          [hidden]="activeTab !== 6"
          [id]="result.probabilityPredictionTableId"
          [showDownload]="true"
        ></table-view-embed>
        <table-view-embed
          *ngIf="result.testProbabilityPredictionTableId"
          [hidden]="activeTab !== 7"
          [id]="result.testProbabilityPredictionTableId"
          [showDownload]="true"
        ></table-view-embed>

        <app-augmentation-summary
          *ngIf="result.augmentationSummary"
          [hidden]="activeTab !== 8"
          [augmentationSummary]="result.augmentationSummary"
        ></app-augmentation-summary>
        <album-view-embed
          *ngIf="result.augmentedSampleAlbum"
          [hidden]="activeTab !== 9"
          [albumId]="result.augmentedSampleAlbum"
        ></album-view-embed>
        <div
          *ngIf="result.trainTimeSpentSummary || result.evaluationTimeSpentSummary"
          [hidden]="activeTab !== 10"
          class="flex-col"
        >
          <div class="panel">
            <div class="panel-body">
              <cv-train-time-spent-summary
                [trainTimeSpentSummary]="result.trainTimeSpentSummary"
                [evaluationTimeSpentSummary]="result.evaluationTimeSpentSummary"
              ></cv-train-time-spent-summary>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
})
export class CvModelExperimentStepViewComponent implements OnInit {
  @Input() result: ICVTLTrainStepResult;
  @Input() pipeline: ICVTLTrainStepParams;
  @Input() architectures: ICVArchitecture[];
  @Input() classifiers: ICVClassifier[];
  @Input() detectors: ICVDetector[];

  readonly config = config;
  readonly trainConfig = trainConfig;
  activeTab: number = 2;
  model: ICVModel;

  readonly _modelLoader: ReactiveLoader<ICVModel, TObjectId>;

  constructor(
    private cvModelService: CVModelService,
  ) {
    this._modelLoader = new ReactiveLoader(id => this.cvModelService.get(id));
    this._modelLoader.subscribe(model => this.model = model);
  }

  ngOnInit() {
    this._modelLoader.load(this.result.cvModelId);
  }
}

