import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { FormGroup } from '@angular/forms';

import * as _ from 'lodash';

import { ParameterValues } from '../core/interfaces/params.interface';

import {
  ICVArchitecture,
  ICVModelUnit,
} from './cv-architecture.interfaces';
import { CVModelCreateComponent } from './cv-model-create.component';
import { CVModelType } from './cv-model.interface';
import { CommonTrainParams } from './cvtl-train.interfaces';

@Component({
  selector: 'cv-model-create-step-advanced',
  template: `
    <div class="panel">
      <div class="panel-body" *ngIf="!!cvArchitecture && !!cvArchitecture.params">
        <app-form-group
          [caption]="cvArchitecture.name + ' Parameters'"
          *ngIf="cvArchitecture.params?.length"
        >
          <train-cv-primitive-params
            [parameters]="cvArchitecture.params"
            (valueChange)="setFeatureExtractorParamsValue($event)"
            (validityChange)="setFeatureExtractorParamsValidity($event)"
          ></train-cv-primitive-params>
        </app-form-group>
      </div>
      <div class="panel-body" *ngIf="!!cvModelUnit && !!cvModelUnit.params">
        <app-form-group
          [caption]="cvModelUnit.name + ' Parameters'"
          *ngIf="cvModelUnit.params?.length"
        >
          <train-cv-primitive-params
            [parameters]="cvModelUnit.params"
            (valueChange)="setParamsValue($event)"
            (validityChange)="setParamsValidity($event)"
          ></train-cv-primitive-params>
        </app-form-group>
      </div>
      <div class="panel-body"
        *ngIf="form.controls['modelType']['controls']['tlType'].value === '${CVModelType.TLType.LOCALIZATION}'"
      >
        <common-train-params
          [albumId]="form.controls['input'].value"
          (valueChange)="onAdvancedChange($event)"
        ></common-train-params>
      </div>
      <div class="pt5 pb15 row">
        <div class="col-md-6">
          <app-check [label]="'Enable Automated Data Augmentation'"
            [control]="form.controls['doAutomatedAugmentation']"></app-check>
        </div>
        <div class="col-md-6">
          <app-check [label]="'Generate augmented sample album'"
            [control]="form.controls['augmentationOptions'].controls.prepareSampleAlbum"></app-check>
        </div>
      </div>
      <app-album-augmentation-params
        [hidden]="!form.value['doAutomatedAugmentation']"
        [value]="form.controls['augmentationOptions'].controls.augmentations.value"
        [bloatFactorControl]="form.controls['augmentationOptions'].controls.bloatFactor"
        (valueChange)="form.controls['augmentationOptions'].controls.augmentations.setValue($event)"
      ></app-album-augmentation-params>
    </div>
  `,
})
export class CvModelCreateStepAdvancedComponent implements OnChanges {
  @Input() form: FormGroup;
  @Input() cvArchitecture: ICVArchitecture;
  @Input() cvModelUnit: ICVModelUnit;

  constructor(private _parent: CVModelCreateComponent) {}

  ngOnChanges(changes: SimpleChanges): void {
    if ('cvArchitecture' in changes && this.cvArchitecture && _.isEmpty(this.cvArchitecture.params)) {
      this.setFeatureExtractorParamsValue({});
      this.setFeatureExtractorParamsValidity(true);
    }
    if ('cvModelUnit' in changes && this.cvModelUnit && _.isEmpty(this.cvModelUnit.params)) {
      this.setParamsValue({});
      this.setParamsValidity(true);
    }
  }

  protected onAdvancedChange(params: CommonTrainParams): void {
    this.form.controls['trainParams'].setValue(params);
  }

  protected setFeatureExtractorParamsValue(value: ParameterValues): void {
    this.form.controls['featureExtractorParams'].setValue(value);
  }

  protected setFeatureExtractorParamsValidity(isValid: boolean): void {
    this.form.controls['featureExtractorParams'].setErrors(isValid ? null : {invalid: true});
  }

  protected setParamsValue(value: ParameterValues): void {
    this.form.controls['params'].setValue(value);
  }

  protected setParamsValidity(isValid: boolean): void {
    this.form.controls['params'].setErrors(isValid ? null : {invalid: true});
  }
}
