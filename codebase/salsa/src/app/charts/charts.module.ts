import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { CoreModule } from '../core/core.module';

import { BarChartComponent } from './bar-chart.component';
import { BoxChartComponent } from './box-chart.component';
import { GaugeChartComponent } from './gauge-chart.component';
import { OneDimScatterChartComponent } from './one-d-scatter-chart.component';
import { ProgressChartComponent } from './progress-chart.component';
import { TwoDimScatterChartComponent } from './two-d-scatter-chart.component';

const CHART_COMPONENTS = [
  OneDimScatterChartComponent,
  TwoDimScatterChartComponent,
  BarChartComponent,
  BoxChartComponent,
  GaugeChartComponent,
  ProgressChartComponent,
];

@NgModule({
  imports: [
    BrowserModule,
    // deep cortex modules
    CoreModule,
  ],
  declarations: [
    ...CHART_COMPONENTS,
  ],
  exports: [
    ...CHART_COMPONENTS,
  ],
  entryComponents: [...CHART_COMPONENTS],
})
export class ChartsModule {}
