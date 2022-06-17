import { Component, Input, OnInit } from '@angular/core';

import { forkJoin } from 'rxjs/observable/forkJoin';

import { IExperimentResultView } from '../experiments/experiment-result.component';
import { IExperimentFull } from '../experiments/experiment.interfaces';
import { ReactiveLoader } from '../utils/reactive-loader';

import { ICVArchitecture, ICVClassifier, ICVDetector } from './cv-architecture.interfaces';
import { CVArchitectureService } from './cv-architecture.service';
import { ICVTLTrainPipeline, ICVTLTrainResult } from './cvtl-train.interfaces';

@Component({
  selector: 'cv-model-experiment-view',
  template: `
    <app-spinner [visibility]="!architectures"></app-spinner>
    <div *ngIf="architectures">
      <ul class="nav nav-tabs" role="tablist" *ngIf="experiment.result.step2">
        <li role="presentation" [ngClass]="{'active': activeTab === 0}">
          <a (click)="activeTab = 0">Step 1</a>
        </li>
        <li role="presentation" [ngClass]="{'active': activeTab === 1}">
          <a (click)="activeTab = 1">Step 2</a>
        </li>
      </ul>

      <cv-model-experiment-step-view
        *ngIf="experiment.result.step1"
        [hidden]="activeTab !== 0"
        [result]="experiment.result.step1"
        [pipeline]="experiment.pipeline.step1"
        [architectures]="architectures"
        [classifiers]="classifiers"
        [detectors]="detectors"
      ></cv-model-experiment-step-view>

      <cv-model-experiment-step-view
        *ngIf="experiment.result.step2"
        [hidden]="activeTab !== 1"
        [result]="experiment.result.step2"
        [pipeline]="experiment.pipeline.step2"
        [architectures]="architectures"
        [classifiers]="classifiers"
        [detectors]="detectors"
      ></cv-model-experiment-step-view>
    </div>
  `,
})
export class CVTLTrainExperimentViewComponent
  implements OnInit, IExperimentResultView<ICVTLTrainPipeline, ICVTLTrainResult> {
  @Input() experiment: IExperimentFull<ICVTLTrainPipeline, ICVTLTrainResult>;

  readonly _architecturesLoader: ReactiveLoader<[ICVArchitecture[], ICVClassifier[], ICVDetector[]], any>;

  architectures: ICVArchitecture[];
  classifiers: ICVClassifier[];
  detectors: ICVDetector[];
  activeTab: number = 0;


  constructor(
    cvArchitectureService: CVArchitectureService,
  ) {
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
    this._architecturesLoader.load();
  }
}

