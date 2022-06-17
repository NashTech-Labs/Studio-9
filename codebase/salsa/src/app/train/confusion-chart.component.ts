import { Component, ElementRef, Input, OnChanges, ViewChild } from '@angular/core';

import { IModelEvaluationSummary } from './model.interface';

interface IConfusionMatrixPreparedRow {
  actual: number;
  predicted: number;
  count: number;
}

@Component({
  selector: 'confusion-chart',
  template: `
    <div #selector style="width:100%;height:100%"></div>
  `,
})
export class ConfusionChartComponent implements OnChanges {
  @Input() data: IModelEvaluationSummary.ConfusionMatrixRow[];
  @ViewChild('selector') private selector: ElementRef;

  ngOnChanges() {
    const classes = this.data.map(_ => _.className);
    if (classes.length !== 2) {
      throw new Error('I can only restore 2-class matrix');
    }

    const matrix: IConfusionMatrixPreparedRow[] = [
      {actual: 0, predicted: 0, count: this.data[0].truePositive},
      {actual: 0, predicted: 1, count: this.data[0].falseNegative},
      {actual: 1, predicted: 0, count: this.data[0].falsePositive},
      {actual: 1, predicted: 1, count: this.data[0].trueNegative},
    ];

    const data = matrix.map((r, i) => this._prepareBox(r, i, classes));

    const layout = {
      autosize: true,
      scene: {
        xaxis: {
          autotick: false,
          dtick: 1,
          tick0: 0,
          type: 'linear',
          zeroline: false,
          title: 'Actual',
        },
        yaxis: {
          autotick: false,
          dtick: 1,
          tick0: 0,
          type: 'linear',
          zeroline: false,
          title: 'Predicted',
        },
        zaxis: {
          type: 'linear',
          zeroline: false,
          title: 'Count',
        },
      },
      title: 'Confusion matrix',
      width: '100%',
      height: 480,
    };

    let defaultPlotlyConfiguration = {
      displayModeBar: false,
      displaylogo: false,
    };

    import('assets/js/plotly.js').then(Plotly => {
      try {
        Plotly.purge(this.selector.nativeElement);
      } catch (e) {
        console.error(`plotly.js purge error: ${e}`);
      }

      try {
        Plotly.newPlot(this.selector.nativeElement, data, layout, defaultPlotlyConfiguration);
      } catch (e) {
        console.error(`plotly.js newPlot error: ${e}`);
      }
    });
  }

  private _prepareBox(row: IConfusionMatrixPreparedRow, i: number, classes: string[]) {
    const colors = [
      'rgb(300,100,200)',
      'rgb(100,200,100)',
      'rgb(100,100,100)',
      'rgb(50,200,150)',
    ];

    const height = row.count,
      x0 = row.actual - 0.45,
      x1 = row.actual + 0.45,
      y0 = row.predicted - 0.45,
      y1 = row.predicted + 0.45;

    return {
      opacity: 1,
      color: colors[i % colors.length],
      type: 'mesh3d',
      name: `A ${classes[row.actual]} P ${classes[row.predicted]}`,
      hoverinfo: 'z+name',
      flatshading: true,
      x: [x0, x1, x1, x0, x0, x1, x1, x0],
      y: [y0, y0, y1, y1, y0, y0, y1, y1],
      z: [0, 0, 0, 0, height, height, height, height],
      i: [0, 1, 1, 2, 2, 3, 3, 0, 0, 0, 4, 4],
      j: [1, 5, 2, 6, 3, 7, 0, 4, 2, 3, 5, 6],
      k: [4, 4, 5, 5, 6, 6, 7, 7, 1, 2, 6, 7],
    };
  }
}
