import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';

import { Subscription } from 'rxjs/Subscription';

import { IAlbum } from '../albums/album.interface';
import config from '../config';
import { LibrarySelectorValue } from '../core/components/library-selector.component';
import { IAsset } from '../core/interfaces/common.interface';
import { ICVModel } from '../train/cv-model.interface';
import { getResultLabelModeByModelType } from '../train/train.helpers';
import { AppFormGroup } from '../utils/forms';
import { MiscUtils } from '../utils/misc';

import { IScriptDeployment } from './script-deployment.interface';

@Component({
  selector: 'deploy-script-deployment-cv-3stl-detection',
  template: `
    <div class="row">
      <div class="col-md-6">
        <library-selector
          [inputLabel]="'Localizer Model'"
          [caption]="'Select Localizer Model'"
          [value]="params?.localizerModelId ? {id: params.localizerModelId, entity: assetType.CV_MODEL } : null"
          [disabled]="disabled"
          [available]="[config.asset.values.CV_MODEL]"
          [itemFilter]="LabelMode.LOCALIZATION | apply: _filterModelOptions"
          (valueChange)="onLocalizerCVModelSelect($event)"
          (valueObjectLoaded)="onLocalizerCVModelLoaded($event)"
        ></library-selector>
      </div>
      <div class="col-md-6">
        <library-selector
          [inputLabel]="'Classifier Model'"
          [caption]="'Select Classifier Model'"
          [value]="params?.classifierModelId ? {id: params.classifierModelId, entity: assetType.CV_MODEL } : null"
          [disabled]="disabled"
          [available]="[config.asset.values.CV_MODEL]"
          [itemFilter]="LabelMode.CLASSIFICATION | apply: _filterModelOptions"
          (valueChange)="onClassifierCVModelSelect($event)"
          (valueObjectLoaded)="onClassifierCVModelLoaded($event)"
        ></library-selector>
      </div>
    </div>

    <ng-container *ngIf="tabs.length">
      <app-tabs [tabs]="tabs" [(active)]="activeTab"></app-tabs>
      <div class="tab-content brand-tab">
        <ng-container
          *ngIf="localizerModel"
          [ngTemplateOutlet]="cvModelInfo"
          [ngTemplateOutletContext]="{ cvModel: localizerModel, activeIndex: 0 }"
        ></ng-container>

        <ng-container
          *ngIf="classifierModel"
          [ngTemplateOutlet]="cvModelInfo"
          [ngTemplateOutletContext]="{ cvModel: classifierModel, activeIndex: 1 }"
        ></ng-container>
      </div>
    </ng-container>

    <ng-template #cvModelInfo let-cvModel="cvModel" let-activeIndex="activeIndex">
      <div role="tabpanel" class="tab-pane" [ngClass]="{'active': activeTab === activeIndex}">
        <div class="panel">
          <div class="panel-body">
            <cv-model-view-embed
              [model]="cvModel"
            ></cv-model-view-embed>
          </div>
        </div>
      </div>
    </ng-template>
  `,
})
export class ScriptDeploymentCv3stlDetectionComponent implements OnChanges, OnInit, OnDestroy {
  @Input() params: IScriptDeployment.Params;
  @Input() disabled: boolean;

  @Output() paramsChange = new EventEmitter<IScriptDeployment.Params>();
  @Output() validityChange = new EventEmitter<boolean>();

  activeTab = 0;
  readonly form: AppFormGroup<{
    localizerModelId: FormControl,
    classifierModelId: FormControl,
  }>;
  readonly config = config;
  readonly LabelMode = IAlbum.LabelMode;
  readonly assetType = IAsset.Type;

  private _localizerModel: ICVModel;
  private _classifierModel: ICVModel;
  private _tabs: string[] = [];
  private _subscription = new Subscription();

  constructor() {
    this.form = new AppFormGroup({
      localizerModelId: new FormControl(null, Validators.required),
      classifierModelId: new FormControl(null, Validators.required),
    });
  }

  get tabs(): string[] {
    return this._tabs;
  }

  get localizerModel(): ICVModel {
    return this._localizerModel;
  }

  set localizerModel(value: ICVModel) {
    this._localizerModel = value;
    if (value) {
      this._tabs[0] = 'Localizer Model Info';
    } else {
      this.tabs.splice(1, 1);
    }
  }

  get classifierModel(): ICVModel {
    return this._classifierModel;
  }

  set classifierModel(value: ICVModel) {
    this._classifierModel = value;
    if (value) {
      this._tabs[1] = 'Classifier Model Info';
      this.activeTab = this._tabs[0] ? this.activeTab : 1;
    } else {
      this.tabs.splice(1, 1);
      this.activeTab = 0;
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ('params' in changes) {
      MiscUtils.fillForm(this.form, this.params, this.disabled);
    }
    if (!this.params) {
      this.localizerModel = undefined;
      this.classifierModel = undefined;
    }
  }

  ngOnInit(): void {
    this._subscription.add(this.form.valueChanges.subscribe(value => this.paramsChange.emit(value)));
    this._subscription.add(this.form.statusChanges.subscribe(() => this.validityChange.emit(this.form.valid)));
  }

  ngOnDestroy(): void {
    this._subscription.unsubscribe();
  }

  onLocalizerCVModelSelect($event: LibrarySelectorValue): void {
    this.form.controls['localizerModelId'].setValue($event.id);
    this.localizerModel = $event.object as ICVModel;
  }

  onClassifierCVModelSelect($event: LibrarySelectorValue): void {
    this.form.controls['classifierModelId'].setValue($event.id);
    this.classifierModel = $event.object as ICVModel;
  }

  onLocalizerCVModelLoaded(asset: IAsset): void {
    this.localizerModel = asset as ICVModel;
  }

  onClassifierCVModelLoaded(asset: IAsset): void {
    this.classifierModel = asset as ICVModel;
  }

  _filterModelOptions = (labelMode: IAlbum.LabelMode): (LibrarySelectorValue) => boolean => {
    return (asset: LibrarySelectorValue): boolean => {
      return asset.entity === IAsset.Type.CV_MODEL
        && getResultLabelModeByModelType((asset.object as ICVModel).modelType) === labelMode;
    };
  }
}
