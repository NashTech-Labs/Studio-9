import { Component, Input } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';

import { IAlbum } from '../albums/album.interface';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { LibrarySelectorValue } from '../core/components/library-selector.component';
import { IAsset, TObjectId } from '../core/interfaces/common.interface';

import { ICVArchitecture, ICVClassifier, ICVDecoder, ICVDetector, ICVModelUnit } from './cv-architecture.interfaces';
import { CVModelCreateComponent } from './cv-model-create.component';
import { CVModelType } from './cv-model.interface';
import { trainConfig } from './train.config';

@Component({
  selector: 'cv-model-create-step',
  template: `
    <div class="row" *ngVar="(form | apply: _modelTypeControl).value as modelTLType">
      <div class="col-md-6">
        <app-select
          [label]="'Model Type'"
          [control]="form | apply: _modelTypeControl"
          [options]="architecture | apply: _modelTypeOptions: tlMode: classifiers: detectors: decoders"
          (valueChange)="_resetInputs()"
          [disabled]="!architecture"
        ></app-select>
      </div>
      <div class="col-md-6"
        [ngSwitch]="modelTLType"
      >
        <app-select
          *ngSwitchCase="'${CVModelType.TLType.CLASSIFICATION}'"
          [label]="'Classifier Type'"
          [control]="form | apply: _modelClassifierTypeControl"
          [options]="classifiers | apply: _classifierOptions: architecture: tlMode"
        ></app-select>
        <app-select
          *ngSwitchCase="'${CVModelType.TLType.LOCALIZATION}'"
          [label]="'Detector Type'"
          [control]="form | apply: _modelDetectorTypeControl"
          [options]="detectors | apply: _detectorOptions: architecture: tlMode"
        ></app-select>
      </div>
    </div>
    <div class="row" *ngVar="(form | apply: _modelTypeControl).value as modelTLType">
      <div class="col-md-6">
        <library-selector
          [inputLabel]="'Train Data'"
          [available]="['${IAsset.Type.ALBUM}']"
          [value]="form.controls['input'].value | apply: _albumSelection"
          (valueChange)="form.controls['input'].setValue($event?.id)"
          [caption]="'Select Training Input Album'"
          [itemFilter]="modelTLType | apply: _albumOptionsFilter"
        ></library-selector>
      </div>
      <div class="col-md-6">
        <library-selector
          [inputLabel]="'Test Data'"
          [available]="['${IAsset.Type.ALBUM}']"
          [value]="form.controls['testInput'].value | apply: _albumSelection"
          (valueChange)="form.controls['testInput'].setValue($event?.id)"
          [caption]="'Select Feature Extractor Training Test Input Album'"
          [allowReset]="true"
          [itemFilter]="modelTLType | apply: _albumOptionsFilter"
        ></library-selector>
      </div>
    </div>
  `,
})
export class CvModelCreateStepComponent {
  @Input() form: FormGroup;
  @Input() architecture: ICVArchitecture;
  @Input() tlMode: boolean = false;
  @Input() classifiers: ICVClassifier[] = [];
  @Input() detectors: ICVDetector[] = [];
  @Input() decoders: ICVDecoder[] = [];

  //noinspection JSUnusedLocalSymbols
  constructor(_parent: CVModelCreateComponent) {}

  readonly _albumOptionsFilter = function(modelType?: CVModelType.TLType): (LibrarySelectorValue) => boolean {
    return (item: LibrarySelectorValue) => {
      switch (item.entity) {
        case IAsset.Type.ALBUM:
          const album = <IAlbum> item.object;
          return modelType ? getAlbumLabelModesByModelType(modelType).includes(album.labelMode) : true;
        default:
          return false;
      }
    };
  };

  _albumSelection = function(albumId: TObjectId): LibrarySelectorValue {
    return albumId
      ? { id: albumId, object: null, entity: IAsset.Type.ALBUM }
      : null;
  };

  _resetInputs() {
    this.form.controls['input'].setValue(undefined);
    this.form.controls['testInput'].setValue(undefined);
  }

  _modelTypeControl = function(form: FormGroup): FormControl {
    return form.controls['modelType']['controls']['tlType'];
  };

  _modelClassifierTypeControl = function(form: FormGroup): FormControl {
    return form.controls['modelType']['controls']['classifierType'];
  };

  _modelDetectorTypeControl = function(form: FormGroup): FormControl {
    return form.controls['modelType']['controls']['detectorType'];
  };

  _modelTypeOptions = function(
    architecture: ICVArchitecture,
    tlMode: boolean,
    classifiers: ICVClassifier[],
    detectors: ICVDetector[],
    decoders: ICVDecoder[],
  ): AppSelectOptionData[] {
    if (!architecture) {
      return [];
    }

    const result: CVModelType.TLType[] = [];

    const supportedClassifiers = classifiers.filter(_ => tlMode || _.isNeural);

    if (supportedClassifiers.length) {
      result.push(CVModelType.TLType.CLASSIFICATION);
    }

    const supportedDetectors = detectors.filter(_ => tlMode || _.isNeural);

    if (supportedDetectors.length) {
      result.push(CVModelType.TLType.LOCALIZATION);
    }

    const supportedDecoders = decoders.filter(_ => !tlMode && _.isNeural);

    if (supportedDecoders.length) {
      result.push(CVModelType.TLType.AUTOENCODER);
    }

    return AppSelectOptionData.fromList(
      result,
      trainConfig.cvModel.tlType.labels,
    );
  };

  _classifierOptions = function(
    classifiers: ICVClassifier[],
    architecture: ICVArchitecture,
    tlMode: boolean,
  ): AppSelectOptionData[] {
    return CvModelCreateStepComponent._cVModelUnitOptions(classifiers, architecture, tlMode);
  };

  _detectorOptions = function(
    detectors: ICVDetector[],
    architecture: ICVArchitecture,
    tlMode: boolean,
  ): AppSelectOptionData[] {
    return CvModelCreateStepComponent._cVModelUnitOptions(detectors, architecture, tlMode);
  };

  static _cVModelUnitOptions = function(
    items: ICVModelUnit[],
    architecture: ICVArchitecture,
    tlMode: boolean,
  ) {
    if (!architecture) {
      return [];
    }

    return items.map((item): AppSelectOptionData => ({
      id: item.id,
      text: `${item.name} (${item.packageName}${'packageVersion' in item ? ' ' + item.packageVersion : ''})`,
      disabled: !(tlMode || item.isNeural),
    }));
  };
}

export function getAlbumLabelModesByModelType(modelType: CVModelType.TLType): IAlbum.LabelMode[] {
  switch (modelType) {
    case CVModelType.TLType.CLASSIFICATION:
      return [IAlbum.LabelMode.CLASSIFICATION];
    case CVModelType.TLType.LOCALIZATION:
      return [IAlbum.LabelMode.LOCALIZATION];
    case CVModelType.TLType.AUTOENCODER:
      return [IAlbum.LabelMode.CLASSIFICATION, IAlbum.LabelMode.LOCALIZATION];
    default:
      throw new Error('I\'m a teapot');
  }
}
