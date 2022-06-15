import { Component } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';

import config from '../../config';
import { AppSelectOptionData } from '../../core-ui/components/app-select.component';

import { ChartOptionsAbstract } from './chart-options';
import { descriptions } from './descriptions';

@Component({
  selector: 'bar-chart-options',
  template: `
    <div class="form-group" *ngIf="form">
      <app-input [label]="'Chart Title'"
        [control]="form.controls['title']"></app-input>
      <app-input [label]="'Chart Subtitle'"
        [control]="form.controls['subtitle']"></app-input>
      <app-input [label]="'Attributes Axis Label'"
        [control]="form.controls['xAxisTitle']"></app-input>
      <app-input [label]="'Metrics Axis Label'"
        [control]="form.controls['yAxisTitle']"></app-input>
<!--
      <app-select [label]="'X-Axis Label Rotation'" [options]="[-90,-45,0,45,90]"
        [control]="form.controls['xAxisAngle']"></app-select>
      <app-select [label]="'Y-Axis Label Rotation'" [options]="[-90,-45,0,45,90]"
        [control]="form.controls['yAxisAngle']"></app-select>
-->
      <app-select
        [label]="'Type'"
        [control]="form.controls['type']"
        [options]="typeOpts">
      </app-select>
      <app-select [label]="'Orientation'"
        [control]="form.controls['orientation']"
        [options]="orientationOpts">
      </app-select>
      <app-check [label]="'Enable Hierarchy Attributes'"
        [control]="form.controls['hierarchy']"
        [helpText]="optionDescriptions.hierarchy"></app-check>
      <app-check [label]="'Vertical Grid'"
        [control]="form.controls['xGrid']"></app-check>
      <app-check [label]="'Horizontal Grid'"
        [control]="form.controls['yGrid']"></app-check>
    </div>
  `,
})

export class BarChartOptionsComponent extends ChartOptionsAbstract {
  readonly config = config;
  readonly optionDescriptions = descriptions;
  readonly typeOpts = AppSelectOptionData.fromList(config.chart.options.type.list, config.chart.options.type.labels);
  readonly orientationOpts = AppSelectOptionData.fromList(config.chart.options.orientation.list, config.chart.options.orientation.labels);

  constructor() {
    super();
    this.form = new FormGroup({
      title: new FormControl(null),
      subtitle: new FormControl(null),
      xAxisTitle: new FormControl(null),
      yAxisTitle: new FormControl(null),
      xAxisAngle: new FormControl(0, Validators.required),
      yAxisAngle: new FormControl(0, Validators.required),
      type: new FormControl(config.chart.options.type.values.GROUPED),
      orientation: new FormControl(config.chart.options.orientation.values.VERTICAL),
      hierarchy: new FormControl(false),
      xGrid: new FormControl(false),
      yGrid: new FormControl(false),
    });
  }
}
