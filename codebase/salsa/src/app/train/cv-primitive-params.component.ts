import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';

import * as _ from 'lodash';
import { concat } from 'rxjs/observable/concat';
import { of } from 'rxjs/observable/of';
import { delay } from 'rxjs/operators/delay';
import { distinctUntilChanged } from 'rxjs/operators/distinctUntilChanged';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { ParameterValueControlComponent } from '../core/components/parameter-value-control.component';
import { ParameterDefinition, ParameterValues } from '../core/interfaces/params.interface';

@Component({
  selector: 'train-cv-primitive-params',
  template: `
    <ng-container>
      <div
        *ngFor="let parameter of parameters | filter: _isParameterVisible: _value"
        class="parameter"
      >
        <div class="parameter-control" *ngVar="form.controls[parameter.name] as control">
          <parameter-value-control
            [parameter]="parameter"
            [control]="control"
          ></parameter-value-control>
        </div>
      </div>
    </ng-container>
  `,
})
export class CVPrimitiveParamsComponent implements OnDestroy, OnChanges {
  config = config;

  @Input() parameters: ParameterDefinition[]; // parameter definitions
  @Input('value') inputValue: ParameterValues = {}; // parameter values
  @Input() disabled: boolean = false;
  @Output() valueChange = new EventEmitter<ParameterValues>();
  @Output() validityChange = new EventEmitter<boolean>();

  collapsed: boolean = false;
  form: FormGroup;

  protected _value: ParameterValues = {};
  private formSubscriptions: Subscription[] = [];

  constructor() {}

  ngOnChanges(changes: SimpleChanges): void {
    if (('parameters' in changes && this.parameters) || 'inputValue' in changes) {
      this.prepareForm();
    }
  }

  _isParameterVisible = (
    definition: ParameterDefinition,
    values: ParameterValues,
  ): boolean => ParameterDefinition.isParameterAvailable(definition, values);

  ngOnDestroy() {
    this.formSubscriptions.forEach(_ => _.unsubscribe());
  }

  protected prepareForm() {
    this.formSubscriptions.forEach(_ => _.unsubscribe());

    const controls = this.parameters.reduce<{[name: string]: FormControl}>((acc, definition) => {
      const value = ParameterValueControlComponent.calculateParameterFormValue(definition, this.inputValue);
      acc[definition.name] = new FormControl(value);
      return acc;
    }, {});

    this.parameters.forEach(definition => {
      const control = controls[definition.name];
      this._applyParameterValidators(definition, control);
    });

    this.form = new FormGroup(controls);

    this.formSubscriptions.push(concat(of(this.form.value), this.form.valueChanges)
      .pipe(delay(0))
      .subscribe(rawValue => {
        const value = Object.keys(rawValue)
          .reduce((params, paramName) => {
            const definition: ParameterDefinition = this.parameters
              .filter(_ => _.name === paramName)[0];

            if (!definition) {
              return params;
            }

            return {
              ...params,
              [paramName]: ParameterValueControlComponent.calculateParameterValue(definition, rawValue[paramName]),
            };
          }, {});

        this._value = value;
        this.valueChange.next(value);
        window.setTimeout(() => this.validityChange.emit(this.form.valid), 0);
      }));
  }

  private _applyParameterValidators(
    definition: ParameterDefinition,
    control: FormControl,
  ): void {
    if (this.disabled) {
      control.disable();
      return;
    }

    control.setValidators(ParameterValueControlComponent.prepareParameterValidator(definition));

    if (definition.conditions) {
      const paramValuesTrigger = this.valueChange
        .map(value => _.pick(value, ...Object.keys(definition.conditions)))
        .pipe(distinctUntilChanged(_.isEqual));

      const subscription =
        paramValuesTrigger
        .subscribe(() => {
          if (this._isParameterVisible(definition, this._value)) {
            control.disabled && control.enable();
          } else {
            control.enabled && control.disable();
          }
        });

      this.formSubscriptions.push(subscription);
    }

    control.updateValueAndValidity();
  }
}
