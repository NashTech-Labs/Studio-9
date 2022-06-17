import * as _ from 'lodash';

import { IAsset, TObjectId } from './common.interface';

export type ParameterValueType = number | string | boolean | number[] | string[] | boolean[] | TObjectId | TObjectId[];
export type ParameterValues = { [key: string]: ParameterValueType };

interface BaseParameterDefinition {
  name: string;
  caption?: string;
  description?: string;
  type: 'int' | 'float' | 'boolean' | 'string' | 'assetReference';
  multiple?: boolean;
  conditions?: { [name: string]: ParameterCondition };
}

export type ParameterDefinition = IntParameterDefinition | FloatParameterDefinition | StringParameterDefinition | BooleanParameterDefinition | AssetReferenceParameterDefinition;

export type ParameterCondition = StringParameterCondition | IntParameterCondition | FloatParameterCondition | BooleanParameterCondition;

export interface IntParameterDefinition extends BaseParameterDefinition {
  type: 'int';
  min?: number;
  max?: number;
  step?: number;
  options?: number[];
  defaults?: number[];
}

export interface FloatParameterDefinition extends BaseParameterDefinition {
  type: 'float';
  min?: number;
  max?: number;
  step?: number;
  options?: number[];
  defaults?: number[];
}

export interface StringParameterDefinition extends BaseParameterDefinition {
  type: 'string';
  options: string[];
  defaults?: string[];
}

export interface BooleanParameterDefinition extends BaseParameterDefinition {
  type: 'boolean';
  defaults?: boolean[];
}

export interface AssetReferenceParameterDefinition extends BaseParameterDefinition {
  type: 'assetReference';
  assetType: IAsset.Type;
}

export interface StringParameterCondition {
  values: string[];
}

export interface IntParameterCondition {
  values?: number[];
  min?: number;
  max?: number;
}

export interface FloatParameterCondition {
  values?: number[];
  min?: number;
  max?: number;
}

export interface BooleanParameterCondition {
  value: boolean;
}


export namespace ParameterDefinition {
  export function isParameterAvailable(
    definition: ParameterDefinition,
    values: ParameterValues,
  ): boolean {
    if (definition.conditions) {
      const results = _.map(definition.conditions, (condition: any, paramName) => {
        const parameter = values[paramName];
        const paramValues: ParameterValueType[] = definition.multiple && _.isString(parameter)
          ? String(parameter)
            .split(new RegExp('[ ,]+'))
            .map(_ => _.trim())
            .filter(_ => !!_.length)
          : [parameter];

        return paramValues.some(
          (value: number | string | boolean) => {
            let result = true;
            if ('values' in condition) {
              result = result && (<any[]> condition.values).includes(value);
            }
            if ('min' in condition) {
              result = result && +value > condition.min;
            }
            if ('max' in condition) {
              result = result && +value < condition.max;
            }
            if ('value' in condition) {
              result = result && condition.value === value;
            }
            return result;
          });
      });

      return _.every(results);
    }

    return true;
  }
}
