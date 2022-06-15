import { Component, ViewChild } from '@angular/core';

import config from '../config';
import { ModalComponent } from '../core-ui/components/modal.component';
import { ParameterDefinition } from '../core/interfaces/params.interface';
import { IPackage } from '../develop/package.interfaces';

import { PipelineDataType, PipelineOperator } from './pipeline.interfaces';

@Component({
  selector: 'operator-info-modal',
  template: `
    <app-modal
      #modal
      [caption]="operator?.name + ' Information'"
      [buttons]="[{'class': 'btn-primary', 'title': 'Close'}]"
      (buttonClick)="modal.hide()"
    >
      <ng-container *ngIf="operator">
        <dl class="dl-horizontal">
          <dt>Operator name</dt>
          <dd>{{operator.name}}</dd>
        </dl>
        <dl class="dl-horizontal">
          <dt>Description</dt>
          <dd>{{operator.description || 'N/A'}}</dd>
        </dl>
        <dl class="dl-horizontal">
          <dt>Package name</dt>
          <dd>{{operator.packageName}}</dd>
        </dl>
        <dl class="dl-horizontal">
          <dt>Package version</dt>
          <dd>{{operator.packageVersion}}</dd>
        </dl>
        <h4>Inputs</h4>
        <ul *ngIf="operator.inputs && operator.inputs.length; else noInputs">
          <li *ngFor="let p of operator.inputs">
            <strong>{{p | apply: getInputName}}</strong> :
            {{p.type | apply: getTypeDescription}}
            {{p.description ? '(' + p.description + ')' : ''}}
          </li>
        </ul>
        <ng-template #noInputs>
          <div>No inputs defined</div>
        </ng-template>
        <h4>Outputs</h4>
        <ul *ngIf="operator.outputs && operator.outputs.length; else noOutputs">
          <li *ngFor="let p of operator.outputs">
            <ng-container *ngIf="p.caption">
              <strong>{{p.caption}}</strong> :
            </ng-container>
            {{p.type | apply: getTypeDescription}}
            {{p.description ? '(' + p.description + ')' : ''}}
          </li>
        </ul>
        <ng-template #noOutputs>
          <div>No outputs defined</div>
        </ng-template>
        <ng-container *ngIf="operator.params && operator.params.length">
          <h4>Parameters</h4>
          <ul *ngIf="operator.params && operator.params.length; else noParameters">
            <li *ngFor="let p of operator.params" [ngSwitch]="p.type">
              <strong>{{p | apply: getParameterName}}</strong> :
              {{p | apply: getParameterType}}
              {{p.description ? '(' + p.description + ')' : ''}}
            </li>
          </ul>
          <ng-template #noParameters>
            <div>No parameters defined</div>
          </ng-template>
        </ng-container>
      </ng-container>
    </app-modal>
  `,
})
export class OperatorInfoModalComponent {
  protected operator: PipelineOperator = null;
  protected package: IPackage = null;

  @ViewChild('modal') protected modal: ModalComponent;

  constructor() {
  }

  public show(operator: PipelineOperator): void {
    this.operator = operator;
    this.modal.show();
  }

  //noinspection JSMethodCanBeStatic
  protected getInputName(input: PipelineOperator.Input): string {
    return (input.caption || input.name) + (input.optional ? ' (optional)' : '');
  }

  //noinspection JSMethodCanBeStatic
  protected getTypeDescription(t: PipelineDataType): string {
    return typeof t === 'string' ? t : t.definition;
  }

  //noinspection JSMethodCanBeStatic
  protected getParameterName(p: ParameterDefinition): string {
    return p.caption || p.name;
  }

  //noinspection JSMethodCanBeStatic
  protected getParameterType(p: ParameterDefinition): string {
    if (p.type === 'assetReference') {
      return config.asset.labels[p.assetType];
    }
    return p.type.charAt(0).toUpperCase() + p.type.slice(1);
  }
}
