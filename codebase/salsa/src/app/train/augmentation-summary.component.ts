import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

import { IAugmentationParamDefinition, IAugmentationSteps, albumsConfig } from '../albums/albums.config';

import { IAugmentationSummary } from './cv-model.interface';

@Component({
  selector: 'app-augmentation-summary',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="panel">
      <div class="panel-body">
        <table width="100%">
          <tbody>
          <ng-container *ngFor="let step of augmentationSummary">
            <tr class="step-title-row">
              <td><strong>{{_albumsConfig.album.augmentationType.labels[step.augmentation.augmentationType]}}</strong>
                <i class="glyphicon glyphicon-question-sign"
                  tooltip
                  data-toggle="tooltip"
                  data-html="true"
                  [tooltipTitle]="_albumsConfig.album.augmentationType.description[step.augmentation.augmentationType]"
                ></i>
              </td>
              <td>{{step.count}} pictures</td>
            </tr>
            <tr>
              <td>Bloat Factor
                <i class="glyphicon glyphicon-question-sign"
                  tooltip
                  data-toggle="tooltip"
                  data-html="true"
                  [tooltipTitle]="bfDescription"></i>
              </td>
              <td>{{step.augmentation.bloatFactor}}</td>
            </tr>
            <tr *ngFor="let parameter of steps[step.augmentation.augmentationType] | keys">
              <td>{{steps[step.augmentation.augmentationType][parameter].caption}}
                <i class="glyphicon glyphicon-question-sign"
                  *ngIf="steps[step.augmentation.augmentationType][parameter]?.description"
                  tooltip
                  data-toggle="tooltip"
                  data-html="true"
                  [tooltipTitle]="steps[step.augmentation.augmentationType][parameter]?.description"></i>
              </td>
              <td [ngSwitch]="steps[step.augmentation.augmentationType][parameter].type">
                <ng-template [ngSwitchCase]="'number'">
                  {{step.augmentation[parameter] | apply: _beautifyNumericValue: decimalPipe}}
                </ng-template>
                <ng-template [ngSwitchCase]="'enum'">
                  {{step.augmentation[parameter] | apply: _beautifyEnumValue: steps[step.augmentation.augmentationType][parameter]}}
                </ng-template>
                <ng-template ngSwitchDefault>
                  {{step.augmentation[parameter]}}
                </ng-template>
              </td>
            </tr>
          </ng-container>
          </tbody>
        </table>
      </div>
    </div>
  `,
  providers: [DecimalPipe],
})
export class AugmentationSummaryComponent {
  @Input() augmentationSummary: IAugmentationSummary[] = [];
  readonly _albumsConfig = albumsConfig;
  readonly steps: IAugmentationSteps = albumsConfig.album.augmentationType.parameters;
  readonly bfDescription = 'Refers to the amount of increase in the size of input data (that was fed to DA) ' +
    'as a result of applied DA (data augmentation)';

  constructor(public decimalPipe: DecimalPipe) {
  }

  _beautifyEnumValue = function (value: string | string[], definition: IAugmentationParamDefinition): string {
    const enumValueName = (value: string): string => {
      return definition.optionNames ? definition.optionNames[value] || String(value) : String(value);
    };

    return AugmentationSummaryComponent._beautifyDAParamValue(value, _ => enumValueName(String(_)));
  };

  _beautifyNumericValue = function (value: number | number[], decimalPipe: DecimalPipe): string {
    return AugmentationSummaryComponent._beautifyDAParamValue(value, _ => decimalPipe.transform(_, '1.0-3'));
  };

  static _beautifyDAParamValue = function<T>(value: T | T[], handler: (T) => string): string {
    if (Array.isArray(value)) {
      return value.map(handler).join(', ');
    }
    return handler(value as T);
  };
}

