import { DecimalPipe } from '@angular/common';
import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostBinding,
  Input,
  OnChanges,
  SimpleChanges,
  ViewChild,
} from '@angular/core';

import { D3ChartHelper } from './d3-chart.helper';

@Component({
  selector: 'progress-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg #canvas width='960' height='500'></svg>
  `,
})
export class ProgressChartComponent implements OnChanges, AfterViewInit {
  @Input() progress: number;
  @Input() label: string;
  @HostBinding('style.height') styleHeight = '100%';
  @HostBinding('style.width') styleWidth = '100%';
  @ViewChild('canvas') private _svgElement: ElementRef;
  private _decimalPipe: DecimalPipe = new DecimalPipe('en-US');

  ngOnChanges(changes: SimpleChanges): void {
    if (this._svgElement) {
      this.drawChart();
    }
  }

  ngAfterViewInit(): void {
    this.drawChart();
  }

  private drawChart() {
    const svg = new D3ChartHelper(this._svgElement.nativeElement);
    svg.margin = { left: 10, right: 10, top: 10, bottom: 10 };
    const backgroundPath = svg.chartArea.selectAll('line.background').data([true]);
    backgroundPath.enter()
      .append('line')
      .attr('class', 'background')
      .merge(backgroundPath)
      .attr('x1', 0)
      .attr('x2', svg.chartSize.width)
      .attr('y1', 0)
      .attr('y2', 0);

    const foregroundPath = svg.chartArea.selectAll('line.foreground').data([true]);
    foregroundPath.enter()
      .append('line')
      .attr('class', 'foreground')
      .merge(foregroundPath)
      .attr('x1', 0)
      .attr('x2', svg.chartSize.width * this.progress)
      .attr('y1', 0)
      .attr('y2', 0);

    // Progress
    const oldLabelText = svg.chartArea.selectAll('text.progress-label').data([true]);
    oldLabelText
      .enter()
      .append('text')
      .attr('x', 0)
      .attr('dy', '1em')
      .attr('class', 'progress-label')
      .attr('font-size', '1em')
      .attr('text-anchor', 'start')
      .merge(oldLabelText)
      .text(`${this.label}`);

    // Progress
    const oldProgressText = svg.chartArea.selectAll('text.progress-text').data([true]);
    oldProgressText
      .enter()
      .append('text')
      .attr('x', svg.chartSize.width)
      .attr('dy', '1em')
      .attr('class', 'progress-text')
      .attr('font-size', '1em')
      .attr('text-anchor', 'end')
      .merge(oldProgressText)
      .text(`${this.formatFloat(this.progress * 100)}%`);
  }

  private formatFloat(value: number): string {
    return this._decimalPipe.transform(value, '1.0-2');
  }
}
