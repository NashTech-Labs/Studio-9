import { Component, Input } from '@angular/core';
import { AbstractControl, FormControl, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

import * as _ from 'lodash';

import config from '../../config';
import { AppSelectOptionData } from '../../core-ui/components/app-select.component';
import { TObjectId } from '../interfaces/common.interface';
import { ParameterDefinition, ParameterValueType, ParameterValues } from '../interfaces/params.interface';

import { LibrarySelectorValue } from './library-selector.component';

type InternalAssetReferenceValueType = LibrarySelectorValue | LibrarySelectorValue[];
type InternalParameterValueType = ParameterValueType | InternalAssetReferenceValueType;

@Component({
  selector: 'parameter-value-control',
  template: `
    <ng-container [ngSwitch]="!!parameter.options?.length">
      <app-select *ngSwitchCase="true"
        [label]="parameter.caption || parameter.name"
        [options]="parameter | apply: _enumOptions"
        [helpText]="parameter.description"
        [multiple]="parameter.multiple"
        [control]="control"
        [fixOverflowClipping]="true"
      ></app-select>
      <ng-container *ngSwitchCase="false" [ngSwitch]="parameter.type">
        <app-input *ngSwitchCase="'string'"
          [label]="parameter.caption || parameter.name"
          [helpText]="parameter.description"
          [type]="'text'"
          [control]="control"
        ></app-input>
        <app-input *ngSwitchCase="'int'"
          [label]="parameter.caption || parameter.name"
          [helpText]="parameter.description"
          [type]="parameter.multiple ? 'text' : 'number'"
          [min]="parameter.min"
          [max]="parameter.max"
          [step]="parameter.step || 1"
          [control]="control"
        ></app-input>
        <app-input *ngSwitchCase="'float'"
          [label]="parameter.caption || parameter.name"
          [helpText]="parameter.description"
          [type]="parameter.multiple ? 'text' : 'number'"
          [min]="parameter.min"
          [max]="parameter.max"
          [step]="parameter.step || 0.01"
          [control]="control"
        ></app-input>
        <app-check *ngSwitchCase="'boolean'"
          [label]="parameter.caption || parameter.name"
          [helpText]="parameter.description"
          [control]="control"
        ></app-check>
        <ng-container *ngSwitchCase="'assetReference'">
          <ng-container *ngIf="!parameter.multiple">
            <library-selector
              title="Select an Asset"
              [inputLabel]="parameter.caption || 'Select ' + config.asset.labels[parameter.assetType]"
              [helpText]="parameter.description"
              [allowReset]="true"
              [available]="[parameter.assetType]"
              [disabled]="control.disabled"
              [value]="control.value"
              (valueChange)="control.setValue($event)"
            ></library-selector>
          </ng-container>

          <ng-container *ngIf="parameter.multiple">
            <library-selector
              title="Select an Asset"
              *ngFor="let idx of _range(control.value.length + 1)"
              [inputLabel]="parameter.caption || 'Select ' + config.asset.labels[parameter.assetType]"
              [helpText]="parameter.description"
              [allowReset]="true"
              [available]="[parameter.assetType]"
              [disabled]="control.disabled"
              [value]="idx < control.value.length ? control.value[idx] : null"
              (valueChange)="updateArrayValue(control, idx, $event)"
            ></library-selector>
          </ng-container>
        </ng-container>
      </ng-container>
    </ng-container>
  `,
})
export class ParameterValueControlComponent {
  @Input() parameter: ParameterDefinition;
  @Input() control: FormControl;

  readonly config = config;

  readonly _enumOptions = function(definition: ParameterDefinition): AppSelectOptionData<string>[] {
    if (definition.type === 'string' || definition.type === 'int' || definition.type === 'float') {
      return (<any[]> definition.options).map(value => {
        return {
          id: value,
          text: String(value),
        };
      });
    }

    return [];
  };

  readonly _range = function(n: number): number[] {
    return Array.from(Array(n).keys());
  };

  updateArrayValue(control: AbstractControl, index: number, value: LibrarySelectorValue): void {
    const values = control.value;
    if (_.isArray(values) && index <= values.length) {
      if (value) {
        values[index] = value;
      } else {
        values.splice(index, 1);
      }
      control.setValue(values);
    }
  }

  static calculateParameterFormValue(
    definition: ParameterDefinition,
    values: ParameterValues = {},
  ): InternalParameterValueType {
    const initialDefaultValue = 'defaults' in definition
      ? (definition.multiple ? definition['defaults'] : definition['defaults'][0])
      : (definition.multiple ? [] : null);

    const value = (definition.name in values)
      ? values[definition.name]
      : initialDefaultValue;

    if (definition.type === 'assetReference') {
      const mapper = (v: TObjectId): LibrarySelectorValue => {
        return v ? <LibrarySelectorValue> { id: v, entity: definition.assetType, object: null } : null;
      };
      return definition.multiple
        ? ((value as TObjectId[]).map(_ => mapper(_)))
        : mapper(<TObjectId> value);
    }

    if ((definition.type === 'int' || definition.type === 'float' || definition.type === 'string') && definition.multiple) {
      if (definition.options && definition.options.length) {
        return Array.isArray(value) ? value : <InternalParameterValueType> [value];
      } else {
        return Array.isArray(value) ? value.join(', ') : value;
      }
    }

    return value;
  }

  static calculateParameterValue(
    definition: ParameterDefinition,
    rawValue: InternalParameterValueType,
  ): ParameterValueType {
    if (definition.multiple && typeof rawValue === 'string') {
      rawValue = String(rawValue)
        .split(/[, ]+/)
        .map(_ => _.trim())
        .filter(_ => !!_.length);
    }

    const mapper: (v: any, f: Function) => any = definition.multiple
      ? (v: any[], f: (v: any) => any) => v.map(f)
      : (v: any, f: (v: any) => any) => f(v);

    switch (definition.type) {
      case 'boolean':
        return mapper(rawValue, _ => !!_);
      case 'int':
      case 'float':
        return mapper(rawValue, _ => +_);
      case 'assetReference':
        if (definition.multiple) {
          rawValue = (<LibrarySelectorValue[]> rawValue).filter(_ => !!_);
        }
        return mapper(rawValue, _ => _ ? _.id : null);
      case 'string':
        return mapper(rawValue, _ => String(_));
    }

    throw new Error('Impossibru');
  }

  static prepareParameterValidator(
    definition: ParameterDefinition,
    valueRequired: boolean | (() => boolean) = true,
  ): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const rawValue = control.value;
      const values: any[] = rawValue === null
        ? []
        : definition.multiple
          ? (Array.isArray(rawValue)
              ? rawValue
              : String(rawValue).split(new RegExp('[ ,]+')).map(_ => _.trim()).filter(_ => !!_.length)
          )
          : [rawValue];

      const _valueRequired = typeof valueRequired === 'function'
        ? valueRequired()
        : !!valueRequired;

      const errors: ValidationErrors = _valueRequired ? Validators.required(control) || {} : {};

      if (!values.length && _valueRequired) {
        errors['required'] = {actualValue: null};
      }

      values.forEach(value => {
        switch (definition.type) {
          case 'int':
          case 'float':
            const floatValue = +value;
            if (isNaN(floatValue)) {
              errors['float'] = {actualValue: value};
            } else if (definition.type === 'int' && floatValue % 1 !== 0) {
              errors['number'] = {actualValue: value};
            } else if ('min' in definition && floatValue < definition.min) {
              errors['min'] = {actualValue: value, min: definition.min};
            } else if ('max' in definition && floatValue > definition.max) {
              errors['max'] = {actualValue: value, max: definition.max};
            } else if (definition.options && !definition.options.includes(floatValue)) {
              errors['enum'] = {actualValue: value, options: definition.options};
            }
            break;
          case 'boolean':
            break;
          case 'string':
            const stringValue = String(value);
            if (definition.options && definition.options.length && !definition.options.includes(stringValue)) {
              errors['enum'] = {actualValue: value, options: definition.options};
            }
        }
      });

      return Object.keys(errors).length > 0 ? errors : null;
    };
  }
}
