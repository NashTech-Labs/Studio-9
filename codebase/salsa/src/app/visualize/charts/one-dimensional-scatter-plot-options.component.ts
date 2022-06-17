import { Component } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';

import { D3ChartHelper } from '../../charts/d3-chart.helper';
import config from '../../config';
import { AppSelectOptionData } from '../../core-ui/components/app-select.component';

import { ChartOptionsAbstract } from './chart-options';
import { DashboardCharts } from './chart.interfaces';

@Component({
  selector: 'one-d-chart-options',
  template: `
    <div class="form-group" *ngIf="form">
      <app-input [label]="'Chart Title'"
        [control]="form.controls['title']"></app-input>
      <app-input [label]="'Chart Subtitle'"
        [control]="form.controls['subtitle']"></app-input>
      <app-input [label]="'Attributes Axis Label'"
        [control]="form.controls['xAxisTitle']"></app-input>
      <app-check
        [label]="'Add Jitter'"
        [control]="form.controls['yJitter']"
      ></app-check>
      <app-select [label]="'Gamma'" [control]="form.controls['categoricalGamma']"
        [options]="gammas">
      </app-select>
      <app-select [label]="'Max Bubble Size (px)'" [control]="form.controls['bubbleSize']"
        [options]="bubbleSizes">
      </app-select>
    </div>
  `,
})

export class OneDimensionalScatterPlotOptionsComponent extends ChartOptionsAbstract {
  config = config;
  readonly gammas = Object.keys(D3ChartHelper.CategoricalGamma);
  readonly bubbleSizes = AppSelectOptionData.fromList([5, 10, 15]);

  constructor() {
    super();
    this.form = new FormGroup({
      title: new FormControl(null),
      subtitle: new FormControl(null),
      xAxisTitle: new FormControl(null),
      yJitter: new FormControl(DashboardCharts.defaultOneDimensionalScatterPlotChartOptions.yJitter),
      categoricalGamma: new FormControl(DashboardCharts.defaultOneDimensionalScatterPlotChartOptions.categoricalGamma),
      bubbleSize: new FormControl(DashboardCharts.defaultOneDimensionalScatterPlotChartOptions.bubbleSize),
    });
  }
}
