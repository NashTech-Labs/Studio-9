import {
  Component,
  Input,
  OnInit,
  ViewChild,
} from '@angular/core';

import { forkJoin } from 'rxjs/observable/forkJoin';

import config from '../config';
import { SaveToLibraryModalComponent } from '../core/components/save-to-library.modal.component';
import { ReactiveLoader } from '../utils/reactive-loader';

import {
  ICVArchitecture,
  ICVClassifier,
  ICVDetector,
} from './cv-architecture.interfaces';
import { CVArchitectureService } from './cv-architecture.service';
import {
  CVModelType,
  ICVModel,
} from './cv-model.interface';
import { CVModelService } from './cv-model.service';
import { trainConfig } from './train.config';

@Component({
  selector: 'cv-model-view-embed',
  template: `
    <app-spinner [visibility]="!model || !architectures"></app-spinner>
    <div class="panel" *ngIf="model && architectures">
      <div class="panel-body">
        <div class="row">
          <div class="col-md-6 col-md-push-6">
            <div class="btn-group pull-right" role="group">
              <button type="button"
                *ngIf="!model.inLibrary"
                class="btn btn-md"
                (click)="saveModel()"
                title="Save Model To The Library"
              ><i class="glyphicon glyphicon-book"></i> Save To The Library</button>
            </div>
          </div>
          <div class="col-md-6 col-md-pull-6">
            <dl>
              <dt>Model Name:</dt>
              <dd>{{model.name}}</dd>
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
      </div>
    </div>
    <save-to-library-modal #saveToLibraryModal [service]="cvModelService"></save-to-library-modal>
  `,
})
export class CVModelViewEmbedComponent implements OnInit {
  readonly config = config;
  readonly trainConfig = trainConfig;

  @Input() model: ICVModel;

  readonly _architecturesLoader: ReactiveLoader<[ICVArchitecture[], ICVClassifier[], ICVDetector[]], any>;

  architectures: ICVArchitecture[];
  classifiers: ICVClassifier[];
  detectors: ICVDetector[];

  @ViewChild('saveToLibraryModal') private saveToLibraryModal: SaveToLibraryModalComponent<ICVModel>;

  constructor(
    protected cvModelService: CVModelService,
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

  saveModel() {
    this.saveToLibraryModal.open(this.model);
  }

}

