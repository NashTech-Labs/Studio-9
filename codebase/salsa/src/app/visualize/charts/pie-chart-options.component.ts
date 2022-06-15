import { Component } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';

import config from '../../config';

import { ChartOptionsAbstract } from './chart-options';

@Component({
  selector: 'pie-chart-options',
  template: `
    <div class="form-group" *ngIf="form">
      <app-input [label]="'Chart Title'"
        [control]="form.controls['title']"></app-input>
      <app-input [label]="'Chart Subtitle'"
        [control]="form.controls['subtitle']"></app-input>
    </div>
  `,
})

export class PieChartOptionsComponent extends ChartOptionsAbstract {
  config = config;

  constructor() {
    super();
    this.form = new FormGroup({
      title: new FormControl(null),
      subtitle: new FormControl(null),
    });
  }
}
