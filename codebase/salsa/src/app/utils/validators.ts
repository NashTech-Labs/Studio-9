import { AbstractControl, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

import * as moment from 'moment';
import { merge } from 'rxjs/observable/merge';
import { of } from 'rxjs/observable/of';
import { map } from 'rxjs/operators/map';
import { Subscription } from 'rxjs/Subscription';

// copied from '@angular/common/src/forms/directives/validators';
type Validation = ValidationErrors | null;

export class AppValidators {

  private static _regex = {
    number: /^-?\d*$/,
    float: /^-?\d*(\.\d+)?$/,
    email: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
    url: /^https?:\/\/(?:www\.|(?!www))[^\s\.]+\.[^\s]{2,}|www\.[^\s]+\.[^\s]{2,}$/,
    password: /^(?=.*?[A-Z])(?=.*?[a-z])(?=.*?[0-9])(?=.*?[^\W\s]).{10,}$/,
    packageVersion: /^(\d+\.){2}(\d+)$/, // according to baile
    packageName: /^([a-z0-9](-[a-z0-9]+)*)+$/,
  };

  private static _validators = {
    number: AppValidators.match(AppValidators._regex.number),
    float: AppValidators.match(AppValidators._regex.float),
    email: AppValidators.match(AppValidators._regex.email),
    url: AppValidators.match(AppValidators._regex.url),
    password: AppValidators.match(AppValidators._regex.password),
    packageVersion: AppValidators.match(AppValidators._regex.packageVersion),
    packageName: AppValidators.match(AppValidators._regex.packageName),
  };

  // to match regex pattern
  static match(pattern: RegExp | string = /^.*$/): ValidatorFn {
    pattern = (typeof pattern === 'string') ? RegExp(<any> pattern) : pattern;

    return (control: AbstractControl): Validation => {
      const value = control.value ? '' + control.value : '';

      return value.match(<RegExp> pattern) ? null :
        {match: {actualValue: value, pattern: pattern}};
    };
  }

  static equals(equalsTo: string, errorMsg: string = null): ValidatorFn {
    return (control: AbstractControl) => {
      if (!control.value || control.value === equalsTo) {
        return null;
      }
      return errorMsg
        ? { errorMsg: { text: errorMsg } }
        : { equals: { actualValue: control.value, requiredValue: equalsTo }};
    };
  }

  /**
   * @deprecated
   */
  static equal(targetControlName: string): ValidatorFn {
    return (control: AbstractControl): Validation => {
      //@todo i guess it's might be other ways to determine wheater control has binded or not
      if (control['_onChange'].length) {
        const targetControl: AbstractControl = control.root.get(targetControlName);

        return targetControl && targetControl.touched && targetControl.value === control.value ? null :
          {equal: {actualValue: control.value, requiredValue: targetControl.value}};
      }
    };
  }

  /**
   * @deprecated
   */
  static notEqual(targetControlName: string, controlName: string): ValidatorFn {
    return (control: AbstractControl): Validation => {
      if (control['_onChange'].length) {
        const targetControl: AbstractControl = control.root.get(targetControlName);

        return targetControl && targetControl.value !== control.value ? null :
        {
          notEqual: {
            actualValue: controlName, // equals %value% in config.validatorErrors
          },
        };
      }
    };
  }

  static number(control: AbstractControl): Validation {
    return !AppValidators._validators.number(control) ? null :
      {number: {actualValue: control.value, pattern: AppValidators._regex.number}};
  }

  static float(control: AbstractControl): Validation {
    return !AppValidators._validators.float(control) ? null :
      {float: {actualValue: control.value, pattern: AppValidators._regex.float}};
  }

  static min(min: number): ValidatorFn {
    return (control: AbstractControl): Validation => {
      let v = +control.value;
      return (!isNaN(v) && v >= +min)
        ? null
        : { min: {actualValue: control.value, min: min} };
    };
  }

  static max(max: number): ValidatorFn {
    return (control: AbstractControl): Validation => {
      let v = +control.value;
      return (!isNaN(v) && v <= +max)
        ? null
        : { max: {actualValue: control.value, max: max} };
    };
  }

  static email(control: AbstractControl): Validation {
    return !AppValidators._validators.email(control) ? null :
      {email: {actualValue: control.value, pattern: AppValidators._regex.email}};
  }

  static url(control: AbstractControl): Validation {
    return !AppValidators._validators.url(control) ? null :
      {url: {actualValue: control.value, pattern: AppValidators._regex.url}};
  }

  static date(format: string = 'YYYY-MM-DD'): (control: AbstractControl) => Validation {
    return function (control: AbstractControl): Validation {
      const date = moment(control.value, format);
      if (!date.isValid()) {
        return {date: {actualValue: control.value, pattern: format}};
      }

      return null;
    };
  }

  static password(control: AbstractControl): Validation {
    return !control.value || !AppValidators._validators.password(control) ? null :
    {password: {actualValue: control.value, pattern: AppValidators._regex.password}};
  }

  static packageVersion(control: AbstractControl): Validation {
    return !AppValidators._validators.packageVersion(control) ? null :
      { packageVersion: { actualValue: control.value, pattern: AppValidators._regex.packageVersion }};
  }

  static packageName(control: AbstractControl): Validation {
    return !AppValidators._validators.packageName(control) ? null :
      { packageName: {actualValue: control.value, pattern: AppValidators._regex.packageName }};
  }


  static isTrue(control: AbstractControl): Validation {
    return control.value === true ? null :
      {isTrue: {actualValue: control.value, requiredValue: true}};
  }

  static isFalse(control: AbstractControl): Validation {
    return control.value === false ? null :
      {isFalse: {actualValue: control.value, requiredValue: false}};
  }

  static custom<T>(f: (value: T) => Validation): ValidatorFn {
    return (control: AbstractControl) => f(control.value);
  }

  static requiredIf(cond: boolean): ValidatorFn {
    return cond ? Validators.required : Validators.nullValidator;
  }

  static crossValidate(
    parentControl: AbstractControl,
    childControls: AbstractControl[],
    fn: (value: any) => ValidatorFn,
    enableFn?: (value: any) => boolean,
  ): Subscription {
    return AppValidators.crossValidateMulti([parentControl], childControls, fn, enableFn);
  }

  static crossValidateMulti(
    parentControls: AbstractControl[],
    childControls: AbstractControl[],
    fn: (...values: any[]) => ValidatorFn,
    enableFn?: (...values: any[]) => boolean,
  ): Subscription {
    return merge(of({}), ...parentControls.map(_ => _.valueChanges))
      .pipe(map(() => parentControls.map(_ => _.value)))
      .subscribe(values => {
        childControls.forEach(childControl => {
          childControl.setValidators(fn(...values));
          childControl.updateValueAndValidity();
        });
        if (enableFn) {
          const enable = enableFn(...values);
          childControls.forEach(childControl => {
            if (childControl.parent && childControl.parent.disabled) {
              return; // skip controls disabled by parent
            }

            if (enable) {
              childControl.enable();
            } else {
              childControl.disable();
            }
          });
        }
      });
  }
}

