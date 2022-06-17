import { Directive, ElementRef, Input, NgZone, OnChanges, OnDestroy } from '@angular/core';

import * as Chart from 'chart.js';

import { IClusteringResult } from './flow.interface';

interface IClusterPoint {
  coords: number[];
  clusterNo: number;
}

/**
 * Usage:
 * <canvas cluster-graph [results]></svg>
 */
@Directive({
  selector: '[cluster-graph]',
})
export class ClusterGraphDirective implements OnChanges, OnDestroy {
  @Input('cluster-data') data: IClusteringResult = null;
  @Input() axisLabels: string[] = [];
  private el: HTMLCanvasElement;
  private chart: Chart;
  private groupColors:  string[] = ['#ff3300', '#009933', '#0066ff', '#ff6600', '#006600', '#000099', '#800000', '#00ff00', '#0099cc', '#996600'];
  private centerColors: string[] = ['#7f1900', '#004d19', '#00337f', '#7f3300', '#003300', '#00004d', '#400000', '#007f00', '#004d66', '#4d3300'];

  constructor(
    el: ElementRef,
    private zone: NgZone,
  ) {
    this.el = el.nativeElement;
  }

  ngOnChanges() {
    if (this.data) {
      this.destroy();
      let result = [];
      let pointGroups = [];
      const centers: IClusterPoint[] = this.data.centers.map(center => {
        const coords = center.slice(0, -1);
        const [clusterNo] = center.slice(-1);
        return { coords, clusterNo };
      });

      //Still need it because of centers disorder
      centers.sort((a, b) => {
        return (a.clusterNo < b.clusterNo) ? -1 : 1;
      });
      centers.forEach(center => {
        pointGroups[center.clusterNo] = [];
      });
      //Add points to groups
      this.data.points.forEach(point => {
        const coords = point.slice(0, -1);
        const [clusterNo] = point.slice(-1);
        pointGroups[clusterNo].push({ x: coords[0], y: coords[1] || 0 });
      });

      centers.forEach(center => {
        let color = this.groupColor(center.clusterNo);
        let centerColor = this.centerColor(center.clusterNo);
        //Add group centers (stars) to legend
        result.push({
          label: `Cluster #${center.clusterNo} center`,
          pointStyle: 'crossRot',
          pointRadius: 10,
          pointBorderWidth: 3,
          pointHoverRadius: 12,
          data: [{ x: center.coords[0], y: center.coords[1] || 0 }],
          showLine: false,
          borderColor: centerColor,
          backgroundColor: centerColor,
          hoverBackgroundColor: centerColor,
        });        //Add groups to legend
        result.push({
          label: `Cluster #${center.clusterNo}`,
          pointRadius: 2,
          pointBorderWidth: 1,
          pointHoverRadius: 3,
          data: pointGroups[center.clusterNo],
          showLine: false,
          borderColor: color,
          backgroundColor: color,
          hoverBackgroundColor: color,
        });
      });
      this.init(result);
    }
  }

  ngOnDestroy() {
    this.destroy();
  }

  groupColor(i: number) {
    return this.groupColors[i % this.groupColors.length];
  }

  centerColor(i: number) {
    return this.centerColors[i % this.centerColors.length];
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
            labels: <Chart.ChartLegendLabelOptions> {
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
          tooltips: {
            enabled: true,
            callbacks: {
              title: () => {
                return '';
              },
              label: (tooltipItem) => {
                return this.axisLabels.length === 2 ? `${tooltipItem.xLabel}, ${tooltipItem.yLabel}` : `${tooltipItem.xLabel}`;
              },
            },
          },
          scales: {
            yAxes: [{
              display: this.axisLabels.length === 2,
              type: 'linear',
              position: 'left',
              scaleLabel: {
                display: true,
                labelString: this.axisLabels.length === 2 ? this.axisLabels[1] : null,
              },
            }],
            xAxes: [{
              type: 'linear',
              position: 'bottom',
              scaleLabel: {
                display: true,
                labelString: this.axisLabels[0],
              },
            }],
          },
        },
      });
    });
  }

  private destroy() {
    if (this.chart) {
      this.chart.destroy();
    }
  }
}
