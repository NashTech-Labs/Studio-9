import { Directive, ElementRef, Input, NgZone, OnChanges, OnDestroy } from '@angular/core';

import * as Chart from 'chart.js';

import { IModelEvaluationSummary } from './model.interface';

@Directive({
  selector: '[roc-chart]',
})
export class ROCChartDirective implements OnChanges, OnDestroy {
  @Input('roc-chart') data: IModelEvaluationSummary.ROCRow[] = null;
  private el: HTMLCanvasElement;
  private chart: any;

  constructor(
    el: ElementRef,
    private zone: NgZone,
  ) {
    this.el = el.nativeElement;
  }

  ngOnChanges() {
    if (this.data) {
      this.destroy();
      let result = [{
        label: 'ROC curve',
        fill: false,
        lineTension: 0.1,
        backgroundColor: 'rgba(75,192,192,0.4)',
        borderColor: 'rgba(75,192,192,1)',
        borderCapStyle: 'butt',
        borderDash: [],
        borderDashOffset: 0.0,
        borderJoinStyle: 'miter',
        pointBorderColor: 'rgba(75,192,192,1)',
        pointBackgroundColor: '#fff',
        pointBorderWidth: 1,
        pointHoverRadius: 5,
        pointHoverBackgroundColor: 'rgba(75,192,192,1)',
        pointHoverBorderColor: 'rgba(220,220,220,1)',
        pointHoverBorderWidth: 2,
        pointRadius: 1,
        pointHitRadius: 10,
        data: this.data.map(row => {
          return {x: row[0], y: row[1]};
        }),
        spanGaps: false,
      }];
      this.init(result);
    }
  }

  ngOnDestroy() {
    this.destroy();
  }

  private destroy() {
    if (this.chart) {
      this.chart.destroy();
    }
  }

  private init(result) {
    this.zone.runOutsideAngular(() => {
      this.chart = new Chart(this.el, {
        type: 'line',
        data: {
          datasets: result,
        },
        options: {
          legend: {
            labels: <any> {
              usePointStyle: true,
            },
          },
          animation: {
            duration: 0,
          },
          hover: {
            mode: 'single',
            animationDuration: 0,
          },
          maintainAspectRatio: false,
          scales: {
            yAxes: [{
              type: 'linear',
              position: 'left',
              scaleLabel: {
                display: true,
                labelString: 'True positive rate',
              },
            }],
            xAxes: [{
              type: 'linear',
              position: 'bottom',
              scaleLabel: {
                display: true,
                labelString: 'False positive rate',
              },
            }],
          },
        },
      });
    });
  }
}
