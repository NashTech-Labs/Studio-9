import { AbstractControl, FormArray, FormControl, FormGroup, ValidatorFn } from '@angular/forms';

export class AppFormArray<F extends AbstractControl> extends FormArray {
  controls: F[];

  value: F['value'][];

  constructor(controls: F[], validators?: ValidatorFn | ValidatorFn[]) {
    super(controls, validators);
  }
}

export class AppFormGroup<T extends {[key: string]: AbstractControl}> extends FormGroup {
  controls: T;

  value: { [P in keyof T]: T[P]['value'] };

  constructor(controls: T, validators?: ValidatorFn | ValidatorFn[]) {
    super(controls, validators);
  }

  public patchValue(
    value: Partial<{ [P in keyof T]: T[P]['value'] }>,
    options?: {
      onlySelf?: boolean;
      emitEvent?: boolean;
    },
  ): void {
    super.patchValue(value, options);
  }
}

export type EntityControls<T> = {
  [P in keyof T]: AbstractControl;
};

type UnionToIntersection<U> =
  (U extends any ? (k: U) => void : never) extends ((k: infer I) => void) ? I : never;

type EntityControlDeepInner<T> =
  T extends any[] ? AppFormArray<AbstractControl> :
  T extends object ? AppFormGroup<{ [P in keyof T]: EntityControlDeep<T[P]> }> :
  FormControl;

export type EntityControlDeep<T> =
  EntityControlDeepInner<UnionToIntersection<T>>;
