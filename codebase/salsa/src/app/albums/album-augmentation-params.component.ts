import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

import * as _ from 'lodash';
import { Observable } from 'rxjs/Observable';
import { concat } from 'rxjs/observable/concat';
import { distinctUntilChanged } from 'rxjs/operators/distinctUntilChanged';
import { Subscription } from 'rxjs/Subscription';

import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { AppFormGroup } from '../utils/forms';
import { AppValidators } from '../utils/validators';

import { IAlbum, IAlbumAugmentParams } from './album.interface';
import { AlbumService } from './album.service';
import { IAugmentationParamDefinition, IAugmentationSteps, albumsConfig } from './albums.config';

@Component({
  selector: 'app-album-augmentation-params',
  template: `
    <div class="panel">
      <div class="panel-body" style="position: relative">

        <div [stickyHeader]="true" class="sticky-header row">
          <div class="col-md-6">
            <h3>Augmentations to apply:</h3>
          </div>
          <div class="col-md-3">
            <div class="pull-right">
              <app-input
                type="number"
                [min]="1"
                [step]="1"
                [label]="'Bloat Factor'"
                [control]="bloatFactorControl"
                [helpText]="_albumsConfig.album.augmentationType.description.BLOAT_FACTOR.COMPOSE"
              ></app-input>
            </div>
          </div>
          <div class="col-md-3">
            <span class="pull-right effective-bloat" *ngIf="form">
              Effective bloat factor
              <i
                class="helpText glyphicon glyphicon-question-sign icon-suffix"
                tooltip
                data-toggle="tooltip"
                data-html="true"
                data-placement="top"
                [tooltipTitle]="_albumsConfig.album.augmentationType.description.BLOAT_FACTOR.EFFECTIVE"
              ></i>:
              <strong>
                {{ value | apply: _calculateEffectiveBloat : bloatFactorControl.value | number: '1.0-3' }}
              </strong>
            </span>
          </div>
        </div>
        <app-spinner [visibility]="!form"></app-spinner>
        <div *ngIf="form" class="panel-group" role="tablist">
          <div *ngFor="let step of steps | keys"
            [capture]="form.controls[step].controls['enable'].value"
            #stepActive="capture"
            class="panel panel-default"
          >
            <div class="panel-heading" role="tab">
              <h4 class="panel-title" style="overflow: hidden">
                <span class="label train-toggle-btn"
                  [ngClass]="stepActive.value ? 'label-success' : 'label-default'"
                  (click)="_toggleStep(step)"
                >
                  <i class="glyphicon"
                    [ngClass]="stepActive.value ? 'glyphicon-check' : 'glyphicon-unchecked'"
                  ></i>
                  {{stepActive.value ? 'do' : 'don\\'t do'}}
                </span>

                <a role="button" (click)="stepActive.value && _toggleStepCollapse(step)"
                  style="padding-right: 100%; margin-right: -100%;"
                >
                  {{_albumsConfig.album.augmentationType.labels[step]}}
                  <i class="glyphicon glyphicon-question-sign"
                    tooltip
                    data-toggle="tooltip"
                    data-html="true"
                    [tooltipTitle]="_albumsConfig.album.augmentationType.description[step]"></i>
                  <i class="glyphicon pull-right"
                    [ngClass]="_expandedSteps.includes(step) ? 'glyphicon-chevron-up' : 'glyphicon-chevron-down'"
                  ></i>
                </a>
              </h4>
            </div>

            <div
              role="tabpanel"
              class="panel-collapse collapse"
              [ngClass]="{'in': _expandedSteps.includes(step)}"
            >
              <div class="panel-body row">
                <div class="col-sm-8">
                  <div *ngFor="let parameter of steps[step] | keys"
                    [ngSwitch]="steps[step][parameter].type"
                  >
                    <app-select *ngSwitchCase="'enum'"
                      [helpText]="steps[step][parameter]?.description"
                      [label]="steps[step][parameter].caption"
                      [options]="steps[step][parameter] | apply: _enumOptions"
                      [multiple]="steps[step][parameter].multiple"
                      [control]="form.controls[step].controls[parameter]"
                    ></app-select>
                    <app-input *ngSwitchCase="'number'"
                      [helpText]="steps[step][parameter]?.description"
                      [label]="steps[step][parameter].caption"
                      [type]="steps[step][parameter].multiple ? 'text' : 'number'"
                      [min]="steps[step][parameter].min"
                      [max]="steps[step][parameter].max"
                      [step]="steps[step][parameter].step || 0.01"
                      [control]="form.controls[step].controls[parameter]"
                    ></app-input>
                    <app-check *ngSwitchCase="'boolean'"
                      [helpText]="steps[step][parameter]?.description"
                      [label]="steps[step][parameter].caption"
                      [control]="form.controls[step].controls[parameter]"
                    ></app-check>
                  </div>
                </div>
                <div class="col-sm-4">
                  <app-input
                    label="Bloat Factor"
                    type="number"
                    [min]="1"
                    [step]="1"
                    [control]="form.controls[step].controls['bloatFactor']"
                    [helpText]="_albumsConfig.album.augmentationType.description.BLOAT_FACTOR.DA_TRANSFORM"
                  ></app-input>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
})
export class AlbumAugmentationParamsComponent implements OnChanges, OnDestroy {
  @Input() bloatFactorControl: FormControl;
  @Input() value: IAlbumAugmentParams.Augmentation[];
  @Output() valueChange = new EventEmitter<IAlbumAugmentParams.Augmentation[]>();
  readonly steps: IAugmentationSteps = albumsConfig.album.augmentationType.parameters;
  readonly _albumsConfig = albumsConfig;
  _expandedSteps: string[] = [];
  form: AppFormGroup<{[K in IAlbum.AugmentationType]: FormGroup}>;

  private formSubscription: Subscription;

  constructor(
    albums: AlbumService,
  ) {
    albums.getAugmentationDefaults().subscribe(defaults => {
      this.prepareForm(defaults);
    });
  }

  ngOnChanges(changes: SimpleChanges) {
    if ('value' in changes && this.form) {
      this.patchForm(this.value);
    }
  }

  _toggleStep(step: IAlbum.AugmentationType) {
    const formControl = this.form.controls[step].controls['enable'];

    if (formControl.value) {
      formControl.setValue(false);
      if (this._expandedSteps.includes(step)) {
        this._expandedSteps = this._expandedSteps.filter(_ => _ !== step);
      }
    } else {
      formControl.setValue(true);
      if (!this._expandedSteps.includes(step)) {
        this._expandedSteps = [step, ...this._expandedSteps];
      }
    }
  }

  _toggleStepCollapse(step: IAlbum.AugmentationType) {
    if (this._expandedSteps.includes(step)) {
      this._expandedSteps = this._expandedSteps.filter(_ => _ !== step);
    } else {
      this._expandedSteps = [step, ...this._expandedSteps];
    }
  }

  _enumOptions = function(definition: IAugmentationParamDefinition): AppSelectOptionData<string>[] {
    return definition.options.map((value, idx) => {
      return {
        id: value,
        text: definition.optionNames ? definition.optionNames[idx] || String(value) : String(value),
      };
    });
  };

  _calculateEffectiveBloat = function(
    params: {[K: string]: (any & {bloatFactor: number})},
    bloatFactor: number,
    albumLength: number = 1000,
  ) {
    const augmentations: IAlbumAugmentParams.Augmentation[] = Object.keys(params)
        .map(_ => ({
          augmentationType: _,
          ...params[_],
        }));

    const composeBloatFactor = Math.min(bloatFactor, augmentations.length);

    const imagesPerTransform = Math.floor((albumLength * composeBloatFactor) / augmentations.length);

    const remainingImages = (albumLength * composeBloatFactor) % augmentations.length;

    const imagesPerAugmentation = augmentations.map(() => imagesPerTransform);
    imagesPerAugmentation[imagesPerAugmentation.length - 1] += remainingImages;

    return _.sum(augmentations.map((options, idx) => {
      const params: any[] = _.values(options);
      const variations = params.filter(_ => Array.isArray(_))
        .map(_ => (<any[]> _).length)
        .reduce((a, b) => a * b, 1);
      return imagesPerAugmentation[idx] * Math.min(variations, options.bloatFactor);
    })) / albumLength;
  };

  ngOnDestroy() {
    this.formSubscription && this.formSubscription.unsubscribe();
  }

  protected prepareForm(defaults: IAlbumAugmentParams.Augmentation[]) {
    const controls = _.mapValues(this.steps, (parameters, augmentationType): FormGroup => {
      const stepDefaults =  defaults.find(_ => _.augmentationType === augmentationType);

      const subControls = _.mapValues(parameters, (parameterOptions: IAugmentationParamDefinition, paramName): FormControl => {
        const defaultValue = stepDefaults ? stepDefaults[paramName] : '';

        // TODO: remove rounding hacks when COR-1409 is fixed
        return new FormControl(
          parameterOptions.type === 'number' && Array.isArray(defaultValue)
            ? defaultValue.map(_ => +parseFloat(_).toFixed(3)).join(', ')
            : (parameterOptions.type === 'number' ? +parseFloat(defaultValue).toFixed(3) : defaultValue),
          [Validators.required, this._validateParameter(parameterOptions)],
        );
      });

      return new FormGroup({
        enable: new FormControl(!!stepDefaults, Validators.required),
        bloatFactor: new FormControl(stepDefaults.bloatFactor, [Validators.required, AppValidators.number, AppValidators.min(1)]),
        ...subControls,
      });
    });

    this.form = new AppFormGroup(controls);

    const oldValue = this.value;

    this.formSubscription = concat(Observable.of(this.form.value), this.form.valueChanges)
      .map((augmentations: {[K: string]: (any & {enable: boolean})}) => {
        return Object.keys(augmentations)
          .filter(_ => augmentations[_].enable)
          .map(augmentationType => {
            const params = _.omit(augmentations[augmentationType], 'enable');
            const definitions: { [P: string]: IAugmentationParamDefinition } = this.steps[augmentationType];

            _.forEach(definitions, (definition: IAugmentationParamDefinition, paramName: string) => {
              if (definition.multiple && typeof params[paramName] === 'string') {
                params[paramName] = String(params[paramName])
                  .split(/[, ]+/)
                  .map(_ => _.trim())
                  .filter(_ => !!_.length);
              }

              const mapper: (v: any, f: Function) => any = definition.multiple
                ? (v: any[], f: (v: any) => any) => v.map(f)
                : (v: any, f: (v: any) => any) => f(v);

              switch (definition.type) {
                case 'boolean':
                  params[paramName] = mapper(params[paramName], _ => !!_);
                  break;
                case 'number':
                  params[paramName] = mapper(params[paramName], _ => +_);
                  break;
              }
            });

            params.bloatFactor = +params.bloatFactor;

            return <IAlbumAugmentParams.Augmentation> {
              augmentationType,
              ...params,
            };
          });
      })
      .pipe(distinctUntilChanged((a, b) => _.isEqual(a, b)))
      .subscribe(value => {
        this.value = value;
        this.valueChange.next(value);
      });

    if (oldValue) {
      this.patchForm(oldValue);
    }
  }

  private patchForm(augmentations: IAlbumAugmentParams.Augmentation[]) {
    const oldValue = this.form.value;

    const value = augmentations.reduce((acc, row) => {
      Object.assign(acc[row.augmentationType], _.omit(row, ['augmentationType']), {enable: true});
      return acc;
    }, Object.keys(oldValue).reduce((acc, key) => {
      acc[key] = {
        ...oldValue[key],
        enable: false,
      };
      return acc;
    }, {}));

    this.form.patchValue(value);
  }

  private _validateParameter(definition: IAugmentationParamDefinition): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const rawValue = control.value;
      const values: any[] = definition.multiple
        ? (Array.isArray(rawValue)
          ? rawValue
          : String(rawValue).split(new RegExp('[ ,]+')).map(_ => _.trim()).filter(_ => !!_.length)
        )
        : [rawValue];

      const errors: ValidationErrors = {};

      if (!values.length) {
        errors['required'] = {actualValue: null};
      }

      values.forEach(value => {
        switch (definition.type) {
          case 'number':
            const floatValue = +value;
            if (isNaN(floatValue)) {
              errors['float'] = {actualValue: value};
            } else if (definition.isInteger && floatValue % 1 !== 0) {
              errors['number'] = {actualValue: value};
            } else if ('min' in definition && floatValue < definition.min) {
              errors['min'] = {actualValue: value, min: definition.min};
            } else if ('max' in definition && floatValue > definition.max) {
              errors['max'] = {actualValue: value, max: definition.max};
            }
            break;
          case 'boolean':
            break;
          case 'enum':
            if (definition.options && !definition.options.includes(value)) {
              errors['enum'] = {actualValue: value, options: definition.options};
            }
        }
      });

      return Object.keys(errors).length > 0 ? errors : null;
    };
  }
}
