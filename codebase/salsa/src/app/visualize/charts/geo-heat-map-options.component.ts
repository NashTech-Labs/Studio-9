import { Component } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';

import config from '../../config';
import { AppSelectOptionData } from '../../core-ui/components/app-select.component';

import { ChartOptionsAbstract } from './chart-options';
import { DashboardCharts } from './chart.interfaces';

@Component({
  selector: 'geo-heat-map-options',
  template: `
    <div class="form-group" *ngIf="form">
      <app-input [label]="'Chart Title'"
        [control]="form.controls['title']"></app-input>
      <app-input [label]="'Chart Subtitle'"
        [control]="form.controls['subtitle']"></app-input>
      <app-select [label]="'Gamma'" [control]="form.controls['gamma']"
        [options]="gammas">
      </app-select>
      <app-select [label]="'Type'" [control]="form.controls['geoType']"
        [options]="geoTypes">
      </app-select>
      <ng-template
        [ngIf]="form.controls['geoType']?.value === config.chart.options.geoType.values.COUNTY">
        <app-select [label]="'State Column'" [control]="form.controls['stateColumn']"
          [options]="state.getWidgetAttributeColumns() | tableColumnSelectOptions">
        </app-select>
        <app-select [label]="'County Column'" [control]="form.controls['countyColumn']"
          [options]="state.getWidgetAttributeColumns() | tableColumnSelectOptions">
        </app-select>
      </ng-template>
      <ng-template [ngIf]="form.controls['geoType']?.value !== config.chart.options.geoType.values.ZIP">
        <app-select [label]="'Mode'" [control]="form.controls['drawMethod']"
          [options]="dotTypes">
        </app-select>
      </ng-template>
      <ng-template [ngIf]="form.controls['drawMethod']?.value === config.chart.options.drawMethod.values.BUBBLE
          || form.controls['geoType']?.value === config.chart.options.geoType.values.ZIP">
        <app-select [label]="'Max Bubble Size (px)'" [control]="form.controls['bubbleSize']"
          [options]="bubbleSizes">
        </app-select>
      </ng-template>
    </div>
  `,
})

export class GeoHeatMapOptionsComponent extends ChartOptionsAbstract {
  readonly config = config;
  readonly gammas = AppSelectOptionData.fromList(config.chart.options.gamma.list, config.chart.options.gamma.labels);
  readonly geoTypes = AppSelectOptionData.fromList(config.chart.options.geoType.list, config.chart.options.geoType.labels);
  readonly bubbleSizes = AppSelectOptionData.fromList([10, 15, 30, 50, 100]);
  readonly dotTypes = AppSelectOptionData.fromList(config.chart.options.drawMethod.list, config.chart.options.drawMethod.labels);

  constructor() {
    super();
    this.form = new FormGroup({
      title: new FormControl(null),
      subtitle: new FormControl(null),
      geoType: new FormControl(DashboardCharts.defaultGeoHeatMapOptions.geoType),
      gamma: new FormControl(DashboardCharts.defaultGeoHeatMapOptions.gamma),
      stateColumn: new FormControl(null),
      countyColumn: new FormControl(null),
      drawMethod: new FormControl(DashboardCharts.defaultGeoHeatMapOptions.drawMethod),
      bubbleSize: new FormControl(DashboardCharts.defaultGeoHeatMapOptions.bubbleSize),
    });
  }
}
