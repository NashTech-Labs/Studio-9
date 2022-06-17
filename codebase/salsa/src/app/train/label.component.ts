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

import { D3ChartHelper } from '../charts/d3-chart.helper';

@Component({
  selector: 'train-label',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg #canvas width='960' height='500'></svg>
  `,
})
export class TrainLabelComponent implements OnChanges, AfterViewInit {
  @Input() label: string;
  @HostBinding('style.width') styleWidth = '100%';
  @HostBinding('style.height') styleHeight = '100%';
  @ViewChild('canvas') private _svgElement: ElementRef;

  ngOnChanges(changes: SimpleChanges): void {
    if (this._svgElement) {
      if (changes['label'] && changes['label'].currentValue !== changes['label'].previousValue) {
        this.drawChart();
      }
    }
  }


  ngAfterViewInit(): void {
    this.drawChart();
  }

  private drawChart() {
    const svg = new D3ChartHelper(this._svgElement.nativeElement);
    svg.margin = { top: 10, left: 0, bottom: 15, right: 10 };
    this._drawDelimiter(svg);
  }

  private _drawDelimiter(svg) {
    // label
    D3ChartHelper.addText(svg.chartArea.node(), [true], 'text-label', 10, 17, this.label);

    const oldLineDelimiter = svg.chartArea.selectAll('line.delimiter').data([true]);
    oldLineDelimiter.enter()
      .append('line')
      .attr('class', 'delimiter')
      .attr('x1', 0)
      .attr('y1', 0)
      .attr('x2', svg.chartSize.width)
      .attr('y2', 0);
    const pathGroup = svg.chartArea.selectAll('g.path-group').data([true]);
    pathGroup.enter()
      .append('g')
      .attr('class', 'path-group')
      .attr('transform', `translate(${svg.chartSize.width - 30}, -10)`);
    const oldPathDelimiter = pathGroup.selectAll('path.delimiter').data([true]);
    oldPathDelimiter.enter()
      .append('path')
      .attr('class', 'delimiter')
      .attr('d', 'M 0 10 L 30 10 L 25 15 L 5 15 Z');
  }
}
