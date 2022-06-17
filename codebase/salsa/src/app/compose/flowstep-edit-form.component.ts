import { Component, Input } from '@angular/core';
import { FormGroup } from '@angular/forms';

import config from '../config';

import { IFlow } from './flow.interface';

@Component({
  selector: 'flowstep-edit-form',
  template: `
    <ng-container [ngSwitch]="form.value.type"
      class="pt15">
      <flowstep-edit-insert *ngSwitchCase="config.flowstep.type.values.insert"
        [flow]="flow"
        [form]="form"></flowstep-edit-insert>
      <flowstep-edit-aggregate *ngSwitchCase="config.flowstep.type.values.aggregate"
        [flow]="flow"
        [form]="form"></flowstep-edit-aggregate>
      <flowstep-edit-join *ngSwitchCase="config.flowstep.type.values.join"
        [flow]="flow"
        [form]="form"></flowstep-edit-join>
      <flowstep-edit-cluster *ngSwitchCase="config.flowstep.type.values.cluster"
        [flow]="flow"
        [form]="form"></flowstep-edit-cluster>
      <flowstep-edit-query *ngSwitchCase="config.flowstep.type.values.query"
        [flow]="flow"
        [form]="form"></flowstep-edit-query>
      <flowstep-edit-filter *ngSwitchCase="config.flowstep.type.values.filter"
        [flow]="flow"
        [form]="form"></flowstep-edit-filter>
      <flowstep-edit-window *ngSwitchCase="config.flowstep.type.values.window"
        [flow]="flow"
        [form]="form"></flowstep-edit-window>
      <flowstep-edit-map *ngSwitchCase="config.flowstep.type.values.map"
        [flow]="flow"
        [form]="form"></flowstep-edit-map>
      <flowstep-edit-geojoin *ngSwitchCase="config.flowstep.type.values.geojoin"
        [flow]="flow"
        [form]="form"></flowstep-edit-geojoin>
      <div *ngSwitchDefault>
        <error-indicator message="This flow step type in not supported"></error-indicator>
      </div>
    </ng-container>
  `,
})
export class FlowstepEditFormComponent {
  config = config;
  @Input() form: FormGroup;
  @Input() flow: IFlow;
}
