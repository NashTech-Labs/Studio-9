import { AbstractControl, FormArray, FormControl, FormGroup } from '@angular/forms';

import * as _ from 'lodash';
import 'rxjs/add/observable/of';
import 'rxjs/add/operator/delay';
import 'rxjs/add/operator/do';
import { Observable } from 'rxjs/Observable';

export class MiscUtils {
  static formatBytes(bytes: number, decimals: number = 2): string {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(decimals))} ${sizes[i]}`;
  }

  static cancelEvent(e?: Event) {
    const event = e || window.event;
    if (event) {
      event.cancelBubble = true;
      event.returnValue = false;
      event.stopPropagation && event.stopPropagation();
      event.preventDefault && event.preventDefault();
    }
  }

  static downloadUrl(url: string, filename?: string): Observable<boolean> {
    if (!('chrome' in window)) {
      let hiddenIFrameID = 'hiddenDownloader',
        iframe = <HTMLIFrameElement> document.getElementById(hiddenIFrameID);

      if (iframe === null) {
        iframe = <HTMLIFrameElement> document.createElement('iframe');
        iframe.id = hiddenIFrameID;
        iframe.style.display = 'none';
        document.body.appendChild(iframe);
      }

      iframe.src = url;
    } else {

      let link = document.createElement('a');
      link.download = filename;
      link.href = url;
      link.click();
    }
    return Observable.of(true).delay(1000);
  }

  static fillForm(form: AbstractControl, data: any, disabled: boolean = false, keepLastArrayMember: boolean = true): void {
    // set initial values for form controls (recursively)
    if (data === undefined) {
      return;
    }
    if (disabled) {
      form.disable();
    } else {
      form.enable();
    }
    // ControlGroup
    if (form instanceof FormGroup) {

      data = data || {};
      const props = Object.keys(form.controls);

      for (let i = 0; i < props.length; ++i) {
        const abstractControlItem = form.controls[props[i]];
        MiscUtils.fillForm(abstractControlItem, data[props[i]], disabled, keepLastArrayMember);
      }
    }

    // ControlArray
    if (form instanceof FormArray) {
      data = data || [];
      // we drop all extra sub forms, but keep at least one
      while (form.length > data.length && form.length > 1) {
        form.removeAt(form.length - 1);
      }
      // in no data to fill, last kept subform gets disabled instead of removing
      if (!data.length && form.controls[0]) {
        keepLastArrayMember
          ? form.controls[0].disable()
          : form.removeAt(0);
      }

      for (let i = 0; i < data.length; ++i) {
        const abstractControlItem = form.controls[i] || MiscUtils.__editFormExtendArray(form);
        MiscUtils.fillForm(abstractControlItem, data[i], disabled, keepLastArrayMember);
      }
    }

    // Control
    if (form instanceof FormControl) {
      form.setValue(data);
    }
  }

  static __editFormExtendArray(controlArray: FormArray): AbstractControl {
    // to extend control array (see _editForm)
    const prototypeControl = controlArray.controls[0] || new FormControl(); // could be even smarter
    const control: AbstractControl = MiscUtils._cloneForm(prototypeControl);
    control.enable();

    controlArray.push(control);
    return control;
  }

  static _cloneForm(form: AbstractControl): AbstractControl {
    // ControlGroup
    if (form instanceof FormGroup) {
      const props = Object.keys(form.controls);
      const control = new FormGroup({}, form.validator, form.asyncValidator);

      for (let i = 0; i < props.length; ++i) {
        const abstractControlItem = form.controls[props[i]];
        control.addControl(props[i], MiscUtils._cloneForm(abstractControlItem));
      }

      return control;
    }

    // ControlArray
    if (form instanceof FormArray) {
      const length = form.controls.length;
      const control = new FormArray([], form.validator, form.asyncValidator);

      for (let i = 0; i < length; ++i) {
        const abstractControlItem = form.controls[i];
        control.push(MiscUtils._cloneForm(abstractControlItem));
      }

      return control;
    }

    // Control
    if (form instanceof FormControl) {
      // how to set default values? :/
      return new FormControl(null, form.validator, form.asyncValidator);
    }

    // TODO: throw error
  }

  static standardDeviation(values) {
    const avg = MiscUtils.average(values);

    const squareDiffs = values.map(function (value) {
      const diff = value - avg;
      return diff * diff;
    });

    const avgSquareDiff = MiscUtils.average(squareDiffs);

    return Math.sqrt(avgSquareDiff);
  }

  static sum(data) {
    return data.reduce(function (sum, value) {
      return sum + value;
    }, 0);
  }

  static average(data) {
    return MiscUtils.sum(data) / data.length;
  }

  static distinctUntilChangedDeep<T>(observable: Observable<T>): Observable<T> {
    let previousValue: T;
    return observable.filter(value => !_.isEqual(value, previousValue)).do(value => {
      previousValue = value;
    });
  }

  static generateUUID() {
    let d = new Date().getTime();
    const uuid = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
      /* tslint:disable:no-bitwise */
      const r = (d + Math.random() * 16) % 16 || 0;
      d = Math.floor(d / 16);
      return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
      /* tslint:enable */
    });
    return uuid;
  }

  static getRandomInt(min, max) {
    return Math.floor(Math.random() * (max - min)) + min;
  }

  static random(min, max) {
    return Math.random() * (max - min) + min;
  }

  static shuffleArray(oldArray) {
    let array = oldArray.slice(0),
      j = 0,
      temp = null;

    for (let i = array.length - 1; i > 0; i -= 1) {
      j = Math.floor(Math.random() * (i + 1));
      temp = array[i];
      array[i] = array[j];
      array[j] = temp;
    }
    return array;
  }
}

export function describeEnum<T extends string, D extends {[p: string]: describeEnum.DescriptionItem<T, any>}>(
  enumeration: {[K in T]: K},
  descriptions: D,
): (D & {values: {[K in T]: T}, list: T[]}) {
  return Object.assign({
    values: enumeration,
    list: <T[]> Object.keys(enumeration),
  }, descriptions);
}

export namespace describeEnum {
  export type DescriptionItem<T extends string, D> = {[K in T]: D};
  export function ensureType<T extends string, D>(description: DescriptionItem<T, D>) {
    return description;
  }
}
