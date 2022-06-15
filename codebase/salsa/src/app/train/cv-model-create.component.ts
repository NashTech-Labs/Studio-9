import {
  Component,
  EventEmitter,
  OnDestroy,
  OnInit,
  Output,
} from '@angular/core';
import {
  FormControl,
  FormGroup,
  Validators,
} from '@angular/forms';

import 'rxjs/add/operator/delay';
import { forkJoin } from 'rxjs/observable/forkJoin';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { LibrarySelectorValue } from '../core/components/library-selector.component';
import {
  IAsset,
  TObjectId,
} from '../core/interfaces/common.interface';
import { ParameterValues } from '../core/interfaces/params.interface';
import { IExperimentPipelineForm } from '../experiments/experiment-pipeline.component';
import { Omit } from '../utils/Omit';
import { ReactiveLoader } from '../utils/reactive-loader';
import { AppValidators } from '../utils/validators';

import {
  ICVArchitecture,
  ICVClassifier,
  ICVDecoder,
  ICVDetector,
  ICVModelUnit,
} from './cv-architecture.interfaces';
import { CVArchitectureService } from './cv-architecture.service';
import {
  CVModelType,
  ICVAugmentationOptions,
  ICVModel,
} from './cv-model.interface';
import {
  CommonTrainParams,
  ICVTLTrainPipeline,
  ICVTLTrainStepParams,
} from './cvtl-train.interfaces';
import { trainConfig } from './train.config';

const enum TrainMode {
  ONE_STEP = 'ONE_STEP',
  TWO_STEP = 'TWO_STEP',
}

const enum TwoStepFEMode {
  CREATE_NEW = 'NEW',
  USE_EXISTING = 'EXISTING',
}

interface ICVModelCreate1StepFormValue {
  name: string;
  description?: string;
  input: TObjectId;
  testInput?: TObjectId;
  architecture: string;
  params?: ParameterValues;
  trainParams?: CommonTrainParams;
  featureExtractorParams?: ParameterValues;
  modelType: CVModelType.TLConsumer;
  augmentationOptions?: ICVAugmentationOptions;
  doAutomatedAugmentation: boolean;
}

interface ICVModelCreateFormValue extends Omit<ICVModelCreate1StepFormValue, 'architecture'> {
  architecture?: string;
  featureExtractorModelId?: TObjectId;
  featureExtractorOptions?: ICVModelCreate1StepFormValue;
  tuneFeatureExtractor: 0 | 1;
  pipeline: TrainMode;
  feMode: TwoStepFEMode;
  doAutomatedAugmentation: boolean;
}

@Component({
  selector: 'cv-model-create',
  template: `
    <app-spinner [visibility]="initialDataLoader.active | async"></app-spinner>
    <div *ngIf="initialDataLoader.loaded">
      <div>
        <div class="row">
          <div class="col-md-6">
            <app-select
              label="Training Mode"
              [control]="form.controls['pipeline']"
              [options]="trainModeOptions"
              [helpText]="trainConfig.hints.cvPipelineHelpText"
              (valueChange)="updatePipeline($event)"
            ></app-select>
          </div>
        </div>
        <!--ONE STEP-->
        <ng-template [ngIf]="form.value['pipeline'] === '${TrainMode.ONE_STEP}'">
          <div class="row">
            <div class="col-md-6">
              <app-select
                [label]="'UTLP'"
                [helpText]="'Untrained Transfer Learning Primitive'"
                [control]="form.controls['architecture']"
                [options]="architectures | map: _architectureToSelectOption"
              ></app-select>
            </div>
          </div>

          <cv-model-create-step
            [form]="form"
            [architecture]="form.controls['architecture'].value | apply: _getArchitecture: architectures"
            [classifiers]="classifiers"
            [detectors]="detectors"
            [decoders]="decoders"
          ></cv-model-create-step>
        </ng-template>

        <!--TWO STEP-->
        <div *ngIf="form.value['pipeline'] === '${TrainMode.TWO_STEP}'">
          <app-form-group [caption]="'Step 1'" *ngVar="form.controls['feMode'].value as step1Mode">
            <div class="row">
              <div class="col-md-6">
                <app-select
                  [label]="'Take feature extractor from'"
                  [control]="form.controls['feMode']"
                  [options]="featureExtractorModeOptions"
                  (valueChange)="setFeatureExtractorMode($event)"
                ></app-select>
              </div>
              <div class="col-md-6" *ngIf="step1Mode === '${TwoStepFEMode.USE_EXISTING}'">
                <library-selector
                  [inputLabel]="'Select Existing Model'"
                  [available]="[config.asset.values.CV_MODEL]"
                  [itemFilter]="_filterFECVModels"
                  [(value)]="_featureExtractorModelSelection"
                  (valueChange)="selectFeatureExtractorModel($event)"
                  [caption]="'Feature Extractor'"
                ></library-selector>
              </div>
            </div>

            <ng-template [ngIf]="step1Mode === '${TwoStepFEMode.CREATE_NEW}'">
              <div class="row">
                <div class="col-md-6">
                  <app-select
                    [label]="'UTLP'"
                    [helpText]="'Untrained Transfer Learning Primitive'"
                    [control]="featureExtractorForm.controls['architecture']"
                    [options]="architectures | map: _architectureToSelectOption"
                  ></app-select>
                </div>
              </div>
              <cv-model-create-step
                class="pt5"
                [form]="featureExtractorForm"
                [architecture]="featureExtractorForm.controls['architecture'].value | apply: _getArchitecture: architectures"
                [classifiers]="classifiers"
                [detectors]="detectors"
                [decoders]="decoders"
              ></cv-model-create-step>
            </ng-template>
          </app-form-group>

          <app-form-group [caption]="'Step 2'">
            <div class="row">
              <div class="col-md-6">
                <app-select
                  [label]="'Tune Feature Extractor'"
                  [helpText]="trainConfig.hints.cvTuneFeatureExtractorText"
                  [control]="form.controls['tuneFeatureExtractor']"
                  [options]="tuneFeatureExtractorOptions"
                ></app-select>
              </div>
            </div>
            <cv-model-create-step
              [tlMode]="true"
              [form]="form"
              [architecture]="(form.controls['feMode'].value === '${TwoStepFEMode.USE_EXISTING}'
                ? _selectedFeatureExtractorModel?.modelType.architecture
                : featureExtractorForm.controls['architecture'].value) | apply: _getArchitecture: architectures"
              [classifiers]="classifiers"
              [detectors]="detectors"
              [decoders]="decoders"
            ></cv-model-create-step>
          </app-form-group>
        </div>
      </div>
      <div class="tabpanel">
        <!-- Nav tabs -->
        <ul class="nav nav-tabs" role="tablist">
          <li role="presentation" [ngClass]="{'active': activeTab === 0}">
            <a (click)="activeTab = 0">Input Album</a>
          </li>
          <li
            *ngIf="!!form.value['testInput']"
            role="presentation"
            [ngClass]="{'active': activeTab === 1}"
          >
            <a (click)="activeTab = 1">Test Album</a>
          </li>
          <ng-container *ngIf="form.value['pipeline'] === '${TrainMode.TWO_STEP}'">
            <ng-template [ngIf]="form.value['feMode'] === '${TwoStepFEMode.CREATE_NEW}'">
              <li
                *ngIf="!!feFormControls['input'].value"
                role="presentation"
                [ngClass]="{'active': activeTab === 2}"
              >
                <a (click)="activeTab = 2">Feature Extractor Input</a>
              </li>
              <li
                *ngIf="!!feFormControls['testInput'].value"
                role="presentation"
                [ngClass]="{'active': activeTab === 3}"
              >
                <a (click)="activeTab = 3">Feature Extractor Test Input</a>
              </li>
            </ng-template>
            <li
              *ngIf="form.value['feMode'] === '${TwoStepFEMode.USE_EXISTING}'
                && !!form.value['featureExtractorModelId']"
              role="presentation"
              [ngClass]="{'active': activeTab === 6}"
            >
              <a (click)="activeTab = 6">Feature Extractor Info</a>
            </li>
            <li
              *ngIf="form.value['pipeline'] === '${TrainMode.TWO_STEP}'
                && form.controls['feMode'].value === '${TwoStepFEMode.CREATE_NEW}'"
              role="presentation"
              [ngClass]="{'active': activeTab === 5, 'bg-danger': !isTab5Valid()}"
            >
              <a (click)="activeTab = 5">Step 1 Advanced Options</a>
            </li>
          </ng-container>
          <li role="presentation" [ngClass]="{'active': activeTab === 4, 'bg-danger': !isTab4Valid()}">
            <a (click)="activeTab = 4">
              <ng-template
                [ngIf]="form.value['pipeline'] === '${TrainMode.TWO_STEP}'
                  && form.controls['feMode'].value === '${TwoStepFEMode.CREATE_NEW}'"
              >
                Step 2
              </ng-template>
              Advanced Options
            </a>
          </li>
        </ul>
      </div>
      <!-- Tab panes -->
      <div class="flex-col" [adaptiveHeight]="{minHeight: 450}">
        <!--input-->
        <div
          *ngIf="!!form.value['input']"
          [hidden]="activeTab !== 0"
          class="panel"
        >
          <album-view-embed [albumId]="form.value['input']"></album-view-embed>
        </div>
        <!--test input-->
        <div
          *ngIf="!!form.value['testInput']"
          [hidden]="activeTab !== 1"
          class="panel"
        >
          <album-view-embed [albumId]="form.value['testInput']"></album-view-embed>
        </div>
        <!--fe input-->
        <div
          *ngIf="!!feFormControls['input'].value"
          [hidden]="activeTab !== 2"
          class="panel"
        >
          <album-view-embed [albumId]="feFormControls['input'].value"></album-view-embed>
        </div>
        <!--fe test input-->
        <div
          *ngIf="!!feFormControls['testInput'].value"
          [hidden]="activeTab !== 3"
          class="panel"
        >
          <album-view-embed [albumId]="feFormControls['testInput'].value"></album-view-embed>
        </div>
        <!--advanced options-->
        <cv-model-create-step-advanced [hidden]="activeTab !== 4"
          [form]="form"
          [cvModelUnit]="form.value['modelType'] | apply: _getModelUnit: classifiers: detectors: decoders"
          [cvArchitecture]="form.value['architecture'] | apply: _getArchitecture: architectures"
        ></cv-model-create-step-advanced>
        <cv-model-create-step-advanced
          *ngIf="form.value['pipeline'] === '${TrainMode.TWO_STEP}'
            && form.controls['feMode'].value === '${TwoStepFEMode.CREATE_NEW}'"
          [hidden]="activeTab !== 5"
          [form]="featureExtractorForm"
          [cvModelUnit]="featureExtractorForm.value['modelType'] | apply: _getModelUnit: classifiers: detectors: decoders"
          [cvArchitecture]="featureExtractorForm.controls['architecture'].value | apply: _getArchitecture: architectures"
        ></cv-model-create-step-advanced>
        <div
          *ngIf="form.value['pipeline'] === '${TrainMode.TWO_STEP}' && !!form.value['featureExtractorModelId']"
          [hidden]="activeTab !== 6"
        >
          <app-spinner [visibility]="!_selectedFeatureExtractorModel"></app-spinner>
          <cv-model-view-embed
            *ngIf="_selectedFeatureExtractorModel"
            [model]="_selectedFeatureExtractorModel"
          ></cv-model-view-embed>
        </div>
      </div>
    </div>
  `,
})
export class CVModelCreateComponent implements OnInit, OnDestroy, IExperimentPipelineForm {
  @Output() validityChange = new EventEmitter<boolean>();
  @Output() dataChange = new EventEmitter<ICVTLTrainPipeline>();

  readonly config = config;
  readonly trainConfig = trainConfig;
  activeTab: number = 0;
  _featureExtractorModelSelection: LibrarySelectorValue;
  _selectedFeatureExtractorModel: ICVModel;

  readonly trainModeOptions = AppSelectOptionData.fromDict(
    {
      [TrainMode.ONE_STEP]: '1-step Training',
      [TrainMode.TWO_STEP]: '2-step (Transfer Learning)',
    },
  );

  readonly featureExtractorModeOptions = AppSelectOptionData.fromDict(
    {
      [TwoStepFEMode.CREATE_NEW]: 'new model',
      [TwoStepFEMode.USE_EXISTING]: 'existing model',
    },
  );

  readonly tuneFeatureExtractorOptions = AppSelectOptionData.fromList(
    [1, 0],
    ['Yes', 'No'],
  );

  form: FormGroup;
  featureExtractorForm: FormGroup;

  readonly initialDataLoader: ReactiveLoader<[ICVArchitecture[], ICVClassifier[], ICVDetector[], ICVDecoder[]], any>;
  architectures: ICVArchitecture[] = [];
  classifiers: ICVClassifier[] = [];
  detectors: ICVDetector[] = [];
  decoders: ICVDecoder[] = [];

  private _formSubscription = new Subscription();

  get feFormControls() {
    return this.featureExtractorForm.controls;
  }

  constructor(
    cvArchitectureService: CVArchitectureService,
  ) {
    // init loaders
    this.initialDataLoader = new ReactiveLoader(() => forkJoin(
      cvArchitectureService.listArchitectures(),
      cvArchitectureService.listClassifiers(),
      cvArchitectureService.listDetectors(),
      cvArchitectureService.listDecoders(),
    ));

    this.initialDataLoader.subscribe(([architectures, classifiers, detectors, decoders]) => {
      this.architectures = architectures;
      this.classifiers = classifiers;
      this.detectors = detectors;
      this.decoders = decoders;

      this._initForm();
    });
  }

  ngOnInit() {
    this.initialDataLoader.load();
  }

  ngOnDestroy() {
    this.initialDataLoader.complete();
    this._formSubscription.unsubscribe();
  }

  _getArchitecture = function(id: string, architectures: ICVArchitecture[]): ICVArchitecture {
    return architectures.filter(_ => _.id === id)[0] || null;
  };

  _architectureToSelectOption = function(architecture: ICVArchitecture): AppSelectOptionData<string> {
    return {
      id: architecture.id,
      text: `${architecture.name} (${architecture.packageName}${'packageVersion' in architecture ? ' ' + architecture.packageVersion : ''})`,
    };
  };

  getPipelineDataToSubmit(): ICVTLTrainPipeline {
    const value: ICVModelCreateFormValue = this.form.value;

    const lastStepParamsDraft: ICVTLTrainStepParams = {
      input: value.input,
      testInput: value.testInput,
      modelType: value.modelType,
      tuneFeatureExtractor: !!value.tuneFeatureExtractor,
      params: value.params,
      trainParams: value.trainParams,
      augmentationOptions: value.doAutomatedAugmentation
        ? {
          augmentations: value.augmentationOptions.augmentations,
          bloatFactor: +value.augmentationOptions.bloatFactor,
          prepareSampleAlbum: !!value.augmentationOptions.prepareSampleAlbum,
        }
        : null,
    };

    if (this.form.value.pipeline === TrainMode.ONE_STEP) {
      return {
        step1: {
          ...lastStepParamsDraft,
          architecture: value.architecture,
          featureExtractorParams: value.featureExtractorParams,
        },
      };
    } else if (this.form.value.pipeline === TrainMode.TWO_STEP) {
      if (this.form.value.feMode === TwoStepFEMode.USE_EXISTING) {
        return {
          step1: {
            ...lastStepParamsDraft,
            featureExtractorModelId: value.featureExtractorModelId,
          },
        };
      } else if (this.form.value.feMode === TwoStepFEMode.CREATE_NEW) {
        return {
          step1: {
            input: value.featureExtractorOptions.input,
            testInput: value.featureExtractorOptions.testInput,
            architecture: value.featureExtractorOptions.architecture,
            modelType: value.featureExtractorOptions.modelType,
            params: value.featureExtractorOptions.params,
            featureExtractorParams: value.featureExtractorOptions.featureExtractorParams,
            augmentationOptions: value.featureExtractorOptions.doAutomatedAugmentation
              ? {
                augmentations: value.featureExtractorOptions.augmentationOptions.augmentations,
                bloatFactor: +value.featureExtractorOptions.augmentationOptions.bloatFactor,
                prepareSampleAlbum: !!value.featureExtractorOptions.augmentationOptions.prepareSampleAlbum,
              }
              : null,
          },
          step2: lastStepParamsDraft,
        };
      }
    }

    throw new Error('I\'m a teapot! Please contact developers.');
  }

  setFeatureExtractorMode(mode: TwoStepFEMode) {
    if (mode === TwoStepFEMode.CREATE_NEW) {
      this.form.controls.featureExtractorModelId.setValue(null);
      this._featureExtractorModelSelection = null;
    } else if (mode === TwoStepFEMode.USE_EXISTING) {
      this.feFormControls.input.setValue(null);
      this.feFormControls.testInput.setValue(null);
      this.form.controls.architecture.setValue(undefined);
    } else {
      throw new Error('I\'m a teapot! Please contact developers.');
    }
  }

  selectFeatureExtractorModel(value: LibrarySelectorValue): void {
    if (value) {
      switch (value.entity) {
        case IAsset.Type.CV_MODEL:
          this.form.controls['featureExtractorModelId'].setValue(value.id);
          const model = <ICVModel> value.object;
          this._selectedFeatureExtractorModel = model;
          this.form.controls['modelType']['controls']['tlType']
            .setValue((<CVModelType.TL> this._selectedFeatureExtractorModel.modelType).tlType);
          break;
        default:
          throw new Error('Can\'t Extract Feature Extractor Model ID');
      }
    } else {
      this.form.controls['featureExtractorModelId'].setValue(null);
    }
  }

  updatePipeline(value) {
    // Reset form to avoid data collisions
    const formControls = this.form.controls;
    const feFormControls = this.featureExtractorForm.controls;

    const defaultFormValues = this._defaultFormValues();

    switch (value) {
      case TrainMode.ONE_STEP:
        // Reset 2-step training fields
        formControls['feMode'].reset(defaultFormValues.feMode);
        this._featureExtractorModelSelection = null;
        feFormControls['architecture'].reset(defaultFormValues.featureExtractorOptions.architecture);
        feFormControls['modelType'].reset(defaultFormValues.featureExtractorOptions.modelType);
        feFormControls['input'].reset(defaultFormValues.featureExtractorOptions.input);
        feFormControls['testInput'].reset(defaultFormValues.featureExtractorOptions.testInput);
        formControls['modelType'].reset(defaultFormValues.modelType);
        formControls['input'].reset(defaultFormValues.input);
        formControls['testInput'].reset(defaultFormValues.testInput);
        formControls['tuneFeatureExtractor'].reset(defaultFormValues.tuneFeatureExtractor);
        break;
      case TrainMode.TWO_STEP:
        // Reset 1-step training fields
        formControls['architecture'].reset(defaultFormValues.architecture);
        formControls['modelType'].reset(defaultFormValues.modelType);
        formControls['input'].reset(defaultFormValues.input);
        formControls['testInput'].reset(defaultFormValues.testInput);
        break;
      default:
        throw new Error('Unknown mode');
    }
  }

  _getModelUnit(
    modelType: CVModelType.TLConsumer,
    classifiers: ICVClassifier[],
    detectors: ICVDetector[],
    decoders: ICVDecoder[],
  ): ICVModelUnit {
    let modelUnit: ICVModelUnit = null;

    if (modelType && modelType.tlType) {
      switch (modelType.tlType) {
        case CVModelType.TLType.CLASSIFICATION:
          modelUnit = classifiers.find(_ => _.id === modelType['classifierType']);
          break;
        case CVModelType.TLType.LOCALIZATION:
          modelUnit = detectors.find(_ => _.id === modelType['detectorType']);
          break;
        case CVModelType.TLType.AUTOENCODER:
          modelUnit = decoders.find(_ => _.id === modelType['decoderType']);
          break;
        default:
          throw new Error('Unknown TL model type');
      }
    }

    return modelUnit;
  }

  protected _filterFECVModels = function(value: LibrarySelectorValue): boolean {
    return value.entity === IAsset.Type.CV_MODEL
      && (value.object as ICVModel).modelType.type === CVModelType.Type.TL;
  };

  protected isTab4Valid(): boolean {
    const paramsControl = this.form.controls['params'];
    return (!paramsControl.enabled || paramsControl.valid);
  }

  protected isTab5Valid(): boolean {
    if (
      this.form.value['pipeline'] === TrainMode.TWO_STEP
      && this.form.controls['feMode'].value === TwoStepFEMode.CREATE_NEW
    ) {
      const form = this.featureExtractorForm;
      return form.controls['featureExtractorParams'].valid && form.controls['params'].valid;
    }
    return true;
  }

  private _initForm() {
    type ClassifierModelType = CVModelType.TLClassifier;
    type DetectorModelType = CVModelType.TLDetector;
    type AutoencoderModelType = CVModelType.TLDecoder;

    const defaultValues = this._defaultFormValues();

    // nested FE Form
    const feDefaults = defaultValues.featureExtractorOptions;

    const feModelTLTypeControl = new FormControl(feDefaults.modelType.tlType, Validators.required);
    const feClassifierTypeControl = new FormControl((<ClassifierModelType> feDefaults.modelType).classifierType);
    const feDetectorTypeControl = new FormControl((<DetectorModelType> feDefaults.modelType).detectorType);
    const feDecoderTypeControl = new FormControl((<AutoencoderModelType> feDefaults.modelType).decoderType);

    const feDoAutomatedAugmentationControl = new FormControl(feDefaults.doAutomatedAugmentation);
    const fePrepareAugmentedSampleAlbumControl = new FormControl(feDefaults.augmentationOptions.prepareSampleAlbum);
    const feAugmentationsControl = new FormControl(feDefaults.augmentationOptions.augmentations);

    this.featureExtractorForm = new FormGroup({
      input: new FormControl(feDefaults.input, Validators.required),
      testInput: new FormControl(feDefaults.testInput),
      architecture: new FormControl(feDefaults.architecture, Validators.required),
      modelType: new FormGroup({
        tlType: feModelTLTypeControl,
        classifierType: feClassifierTypeControl,
        detectorType: feDetectorTypeControl,
        decoderType: feDecoderTypeControl,
      }),
      doAutomatedAugmentation: feDoAutomatedAugmentationControl,
      augmentationOptions: new FormGroup({
        augmentations: feAugmentationsControl,
        prepareSampleAlbum: fePrepareAugmentedSampleAlbumControl,
        bloatFactor: new FormControl(defaultValues.augmentationOptions.bloatFactor, Validators.min(0)),
      }),
      featureExtractorParams: new FormControl({}),
      params: new FormControl({}),
      trainParams: new FormControl({}),
    });

    const modelTLTypeControl = new FormControl(defaultValues.modelType.tlType, Validators.required);
    const classifierTypeControl = new FormControl((<ClassifierModelType> feDefaults.modelType).classifierType);
    const detectorTypeControl = new FormControl((<DetectorModelType> feDefaults.modelType).detectorType);
    const decoderTypeControl = new FormControl((<AutoencoderModelType> feDefaults.modelType).decoderType);

    // global Form
    const pipelineControl = new FormControl(defaultValues.pipeline, Validators.required);
    const extractorControl = new FormControl(defaultValues.featureExtractorModelId);
    const architectureControl = new FormControl(defaultValues.architecture);
    const doAutomatedAugmentationControl = new FormControl(defaultValues.doAutomatedAugmentation);
    const prepareAugmentedSampleAlbumControl = new FormControl(defaultValues.augmentationOptions.prepareSampleAlbum);
    const augmentationsControl = new FormControl(defaultValues.augmentationOptions.augmentations);
    const feModeControl = new FormControl(defaultValues.feMode);
    const feParamsControl = new FormControl({});

    this.form = new FormGroup({
      feMode: feModeControl,
      input: new FormControl(defaultValues.input, Validators.required), // sends on server control
      pipeline: pipelineControl,
      testInput: new FormControl(defaultValues.testInput),
      featureExtractorModelId: extractorControl,
      architecture: architectureControl,
      modelType: new FormGroup({
        tlType: modelTLTypeControl,
        classifierType: classifierTypeControl,
        detectorType: detectorTypeControl,
        decoderType: decoderTypeControl,
      }),
      tuneFeatureExtractor: new FormControl(defaultValues.tuneFeatureExtractor),
      featureExtractorOptions: this.featureExtractorForm,
      doAutomatedAugmentation: doAutomatedAugmentationControl,
      augmentationOptions: new FormGroup({
        augmentations: augmentationsControl,
        prepareSampleAlbum: prepareAugmentedSampleAlbumControl,
        bloatFactor: new FormControl(defaultValues.augmentationOptions.bloatFactor, Validators.min(0)),
      }),
      featureExtractorParams: feParamsControl,
      params: new FormControl({}),
      trainParams: new FormControl({}),
    });

    // emit initial validity
    this.validityChange.emit(this.form.valid);

    this._formSubscription.add(AppValidators.crossValidate(
      doAutomatedAugmentationControl,
      [prepareAugmentedSampleAlbumControl],
      () => Validators.nullValidator,
      checked => checked,
    ));

    this._formSubscription.add(AppValidators.crossValidate(
      doAutomatedAugmentationControl,
      [augmentationsControl],
      (checked: boolean) => AppValidators.requiredIf(checked),
      checked => checked,
    ));

    this._formSubscription.add(doAutomatedAugmentationControl.valueChanges.subscribe(() => {
      prepareAugmentedSampleAlbumControl.setValue(false);
    }));

    this._formSubscription.add(AppValidators.crossValidate(
      feDoAutomatedAugmentationControl,
      [fePrepareAugmentedSampleAlbumControl],
      () => Validators.nullValidator,
      checked => checked,
    ));

    this._formSubscription.add(AppValidators.crossValidate(
      feDoAutomatedAugmentationControl,
      [feAugmentationsControl],
      (checked: boolean) => AppValidators.requiredIf(checked),
      checked => checked,
      ),
    );

    this._formSubscription.add(feDoAutomatedAugmentationControl.valueChanges.subscribe(() => {
      fePrepareAugmentedSampleAlbumControl.setValue(false);
    }));

    this._formSubscription.add(AppValidators.crossValidate(
      modelTLTypeControl,
      [classifierTypeControl],
      (modelType) => AppValidators.requiredIf(modelType === CVModelType.TLType.CLASSIFICATION),
      (modelType) => modelType === CVModelType.TLType.CLASSIFICATION,
    ));

    this._formSubscription.add(AppValidators.crossValidate(
      modelTLTypeControl,
      [detectorTypeControl],
      (modelType) => AppValidators.requiredIf(modelType === CVModelType.TLType.LOCALIZATION),
      (modelType) => modelType === CVModelType.TLType.LOCALIZATION,
    ));

    this._formSubscription.add(AppValidators.crossValidate(
      modelTLTypeControl,
      [decoderTypeControl],
      (modelType) => AppValidators.requiredIf(modelType === CVModelType.TLType.AUTOENCODER),
      (modelType) => modelType === CVModelType.TLType.AUTOENCODER,
    ));

    this._formSubscription.add(AppValidators.crossValidate(
      feModelTLTypeControl,
      [feClassifierTypeControl],
      (modelType) => AppValidators.requiredIf(modelType === CVModelType.TLType.CLASSIFICATION),
      (modelType) => modelType === CVModelType.TLType.CLASSIFICATION,
    ));

    this._formSubscription.add(AppValidators.crossValidate(
      feModelTLTypeControl,
      [feDetectorTypeControl],
      (modelType) => AppValidators.requiredIf(modelType === CVModelType.TLType.LOCALIZATION),
      (modelType) => modelType === CVModelType.TLType.LOCALIZATION,
    ));

    this._formSubscription.add(AppValidators.crossValidate(
      feModelTLTypeControl,
      [feDecoderTypeControl],
      (modelType) => AppValidators.requiredIf(modelType === CVModelType.TLType.AUTOENCODER),
      (modelType) => modelType === CVModelType.TLType.AUTOENCODER,
    ));

    this._formSubscription.add(AppValidators.crossValidate(
      pipelineControl,
      [feModeControl],
      (pipeline) => AppValidators.requiredIf(pipeline === TrainMode.TWO_STEP),
      (pipeline) => pipeline === TrainMode.TWO_STEP,
    ));

    this._formSubscription.add(AppValidators.crossValidateMulti(
      [pipelineControl, feModeControl],
      [this.featureExtractorForm],
      () => Validators.nullValidator,
      (pipeline, feMode) => pipeline === TrainMode.TWO_STEP && feMode === TwoStepFEMode.CREATE_NEW,
    ));

    this._formSubscription.add(AppValidators.crossValidateMulti(
      [pipelineControl, feModeControl],
      [extractorControl],
      (pipeline, feMode) => AppValidators.requiredIf(
        pipeline === TrainMode.TWO_STEP && feMode === TwoStepFEMode.USE_EXISTING,
      ),
      (pipeline, feMode) => pipeline === TrainMode.TWO_STEP && feMode === TwoStepFEMode.USE_EXISTING,
    ));

    this._formSubscription.add(AppValidators.crossValidate(
      pipelineControl,
      [architectureControl],
      pipeline => AppValidators.requiredIf(pipeline === TrainMode.ONE_STEP),
      pipeline => pipeline === TrainMode.ONE_STEP,
    ));

    this._formSubscription.add(AppValidators.crossValidate(
      pipelineControl,
      [feParamsControl],
      () => Validators.nullValidator,
      pipeline => pipeline === TrainMode.ONE_STEP,
    ));

    this._formSubscription.add(this.form.statusChanges.subscribe(() => this.validityChange.emit(this.form.valid)));

    this._formSubscription.add(this.form.valueChanges.delay(100).subscribe(() => {
      return this.form.valid ? this.dataChange.emit(this.getPipelineDataToSubmit()) : null;
    }));
  }

  private _defaultFormValues(): ICVModelCreateFormValue {
    return {
      feMode: TwoStepFEMode.USE_EXISTING,
      name: null,
      input: null,
      pipeline: TrainMode.TWO_STEP,
      testInput: null,
      featureExtractorModelId: null,
      architecture: null,
      modelType: {
        tlType: null,
        classifierType: null,
        detectorType: null,
        decoderType: this.decoders[0].id,
      },
      tuneFeatureExtractor: 0,
      featureExtractorOptions: {
        name: null,
        input: null,
        testInput: null,
        architecture: null,
        modelType: {
          tlType: null,
          classifierType: null,
          detectorType: null,
          decoderType: this.decoders[0].id,
        },
        doAutomatedAugmentation: false,
        augmentationOptions: {
          augmentations: null,
          prepareSampleAlbum: false,
          bloatFactor: 1,
        },
      },
      doAutomatedAugmentation: false,
      augmentationOptions: {
        augmentations: null,
        prepareSampleAlbum: false,
        bloatFactor: 1,
      },
    };
  }
}
