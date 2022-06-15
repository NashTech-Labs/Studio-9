import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges,
} from '@angular/core';
import { FormControl } from '@angular/forms';

import 'rxjs/add/operator/takeUntil';
import { Observable } from 'rxjs/Observable';
import { Subject } from 'rxjs/Subject';

import { IAlbumTagsSummary } from '../albums/album.interface';
import { AlbumService } from '../albums/album.service';
import { AppSelectOptionsType } from '../core-ui/components/app-select.component';
import { TObjectId } from '../core/interfaces/common.interface';
import { AppFormGroup } from '../utils/forms';
import { ReactiveLoader } from '../utils/reactive-loader';

import { CommonTrainParams, LabelOfInterest } from './cvtl-train.interfaces';
import { trainConfig } from './train.config';
import {
  prepareInputSizeOptions,
  prepareInputSizeValueMap,
} from './train.helpers';


interface FormValue {
  inputSize: string;
  loi: LabelOfInterest[];
  defaultVisualThreshold: number[];
  iouThreshold: number[];
  featureExtractorLearningRate: number;
  modelLearningRate: number;
}

@Component({
  selector: 'common-train-params',
  template: `
    <app-form-group
      [caption]="(labelPrefix ? labelPrefix + ' ' : '') + 'Object Detection Parameters'"
    >
      <app-slider
        [min]="0"
        [max]="1"
        [step]="0.01"
        [range]="false"
        label="Visual Threshold"
        [helpText]="'${trainConfig.hints.defaultVisualThresholdHelpText}'"
        [value]="form.controls.defaultVisualThreshold.value"
        (valueChange)="onVisualThresholdChange($event)"
      ></app-slider>

      <app-slider
        [min]="0"
        [max]="1"
        [step]="0.01"
        [range]="false"
        label="IoU Threshold"
        [value]="form.controls.iouThreshold.value"
        (valueChange)="onIouThresholdChange($event)"
        [helpText]="'${trainConfig.hints.iouThresholdHelpText}'"
      ></app-slider>

      <labels-of-interest
        [labels]="albumTags"
        (valueChange)="onLoiChange($event)"
      ></labels-of-interest>
    </app-form-group>

    <app-form-group
      [caption]="(labelPrefix ? labelPrefix + ' ' : '') + 'Training Process Parameters'"
    >
      <app-input
        type="number"
        [control]="form.controls.featureExtractorLearningRate"
        [step]="${1e-5}"
        label="Backbone Learning rate"
        [helpText]="'${trainConfig.hints.featureExtractorLearningRate}'"
      ></app-input>

      <app-input
        type="number"
        [control]="form.controls.modelLearningRate"
        [step]="${1e-5}"
        label="Consumer Learning rate"
        [helpText]="'${trainConfig.hints.modelLearningRate}'"
      ></app-input>

      <app-select
        [control]="form.controls.inputSize"
        [options]="inputSizeOptions"
        label="Input Size"
        [helpText]="'${trainConfig.hints.inputSizeHelpText}'"
      ></app-select>
    </app-form-group>
  `,
})
export class CommonTrainParamsComponent  implements OnInit, OnDestroy, OnChanges {
  @Input() trainParams: CommonTrainParams;
  @Input() albumId: TObjectId;
  @Input() labelPrefix: string;

  @Output() valueChange = new EventEmitter<CommonTrainParams>();

  form: AppFormGroup<{
    inputSize: FormControl,
    loi: FormControl,
    defaultVisualThreshold: FormControl,
    iouThreshold: FormControl,
    featureExtractorLearningRate: FormControl,
    modelLearningRate: FormControl,
  }>;

  protected albumTags: string[] = [];
  protected inputSizeOptions: AppSelectOptionsType = [];

  private debouncer = new Subject<FormValue>();
  private init$ = new Subject<boolean>();
  private destroy$ = new Subject<boolean>();

  private inputSizeValuesMap: { [optionsValue: string]: CommonTrainParams.InputSize };

  private _loader: ReactiveLoader<IAlbumTagsSummary, TObjectId>;

  constructor(
    private albumService: AlbumService,
  ) {
    this._initDebouncer();
    this._prepareInputSizeOptions();

    this._loader = new ReactiveLoader((albumId: TObjectId) => {
      return this.albumService.getTags(albumId);
    });

    this._loader.subscribe((tags: IAlbumTagsSummary) => {
      this.albumTags = tags.map(tag => tag.label);
    });

    this._initForm();
  }

  ngOnInit(): void {
  }

  ngOnDestroy(): void {
    this.destroy$.next(true);
    this.destroy$.complete();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['albumId'] && changes['albumId'].currentValue) {
      this._loader.load(changes['albumId'].currentValue);
    }
  }

  protected onVisualThresholdChange(value: number[]): void {
    this.debouncer.next({ ...this.form.value, defaultVisualThreshold: value });
  }

  protected onIouThresholdChange(value: number[]): void {
    this.debouncer.next({ ...this.form.value, iouThreshold: value });
  }

  protected onLoiChange(value: LabelOfInterest[]): void {
    this.debouncer.next({ ...this.form.value, loi: value });
  }

  private _initDebouncer(): void {
    this.debouncer
      .takeUntil(this.destroy$)
      .debounceTime(50)
      .subscribe((value) => {
        this.form.setValue(value);
      });
  }

  private _initForm(): void {
    this.init$.next(true);

    this.form = new AppFormGroup({
      inputSize: new FormControl('512*512'),
      loi: new FormControl(null),
      defaultVisualThreshold: new FormControl([0.3]),
      iouThreshold: new FormControl([0.45]),
      featureExtractorLearningRate: new FormControl(1e-4),
      modelLearningRate: new FormControl(1e-4),
    });

    this.form.valueChanges
      .takeUntil(Observable.merge(this.destroy$, this.init$))
      .subscribe((value) => {
        if (this.form.valid) {
          this.valueChange.emit(this._exportValue(value));
        }
      });
  }

  private _exportValue(internalValue: FormValue): CommonTrainParams {
    return {
      ...internalValue,
      defaultVisualThreshold: internalValue.defaultVisualThreshold[0] || null,
      iouThreshold: internalValue.iouThreshold[0] || null,
      inputSize: this.inputSizeValuesMap[internalValue.inputSize] || null,
    };
  }

  private _prepareInputSizeOptions(): void {
    this.inputSizeOptions = prepareInputSizeOptions(trainConfig.inputSizes);
    this.inputSizeValuesMap = prepareInputSizeValueMap(trainConfig.inputSizes);
  }

}
