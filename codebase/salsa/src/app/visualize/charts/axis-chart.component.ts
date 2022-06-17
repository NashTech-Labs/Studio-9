import { ElementRef } from '@angular/core';

import * as d3 from 'd3';

import config from '../../config';
import { EventService } from '../../core/services/event.service';
import { ExperimentService } from '../../experiments/experiment.service';
import { TTableValue } from '../../tables/table.interface';
import { TableService } from '../../tables/table.service';
import { ModelService } from '../../train/model.service';
import { CrossFilterBus } from '../cross-filter-bus';
import { VisualizeDataService } from '../visualize-data.service';
import { TabularDataResponse } from '../visualize.interface';

import { DashboardCharts } from './chart.interfaces';
import { D3Chart } from './d3-chart.component';

interface IAttributeGroup {
  attr: TTableValue;
  start: number;
  end: number;
  level: number;
}

const offset = 7;
const shift = 20;
const DIVIDER = '||';

export abstract class AxisChart<T extends DashboardCharts.IAxisChartOptions> extends D3Chart<T> {
  protected attributeScale: d3.ScaleBand<number>;
  protected metricScale: d3.ScaleLinear<number, number>;
  protected yMin: number;
  protected yMax: number;
  protected xz: number[];
  protected yz: number[][];
  protected _rowsLimit = 100;

  constructor(
    tables: TableService,
    models: ModelService,
    experiments: ExperimentService,
    bus: CrossFilterBus,
    dataFetch: VisualizeDataService,
    el: ElementRef, events: EventService,
  ) {
    super(tables, models, experiments, bus, dataFetch, el, events);
  }

  visualizeData(data: TabularDataResponse) {
    this.margin = { top: 40, right: 20 + this.potentialLegendWidth, bottom: 80, left: 80 };
    super.visualizeData(data);

    if (!data || !data.data || !data.data[0]) {
      this.preparedSeries = [];
      return;
    }

    this.prepareSeries(data);

    this.xz = d3.range(this.rowsCount);
    this.yz = d3.range(this.seriesCount).map((i) => {
      return this.preparedSeries[i].values.map(row => {
        return row.value;
      });
    });
    this.yMin = d3.min(this.yz, (y) => {
      return d3.min(y);
    });
    this.yMax = d3.max(this.yz, (y) => {
      return d3.max(y);
    });

    if (!('orientation' in this.options) || this.options.orientation === config.chart.options.orientation.values.VERTICAL) {
      this.attributeScale = d3.scaleBand<number>()
        .domain(this.xz)
        .rangeRound([0, this.width])
        .padding(0.08);

      this.metricScale = d3.scaleLinear()
        .domain([Math.min(this.yMin, 0), Math.max(this.yMax, 0)])
        .range([this.height, 0])
        .nice(10);
    } else {
      this.attributeScale = d3.scaleBand<number>()
        .domain(this.xz)
        .rangeRound([0, this.height])
        .padding(0.08);

      this.metricScale = d3.scaleLinear()
        .domain([Math.min(this.yMin, 0), Math.max(this.yMax, 0)])
        .range([0, this.width])
        .nice(10);
    }
  }

  // gridlines in x axis function
  makeXGridlines() {
    if (!('orientation' in this.options) || this.options.orientation === config.chart.options.orientation.values.VERTICAL) {
      return d3.axisBottom(this.attributeScale)
        .ticks(5);
    } else {
      return d3.axisBottom(this.metricScale)
        .ticks(5);
    }
  }

  // gridlines in y axis function
  makeYGridlines() {
    if (!('orientation' in this.options) || this.options.orientation === config.chart.options.orientation.values.VERTICAL) {
      return d3.axisLeft(this.metricScale)
        .ticks(5);
    } else {
      return d3.axisLeft(this.attributeScale)
        .ticks(5);
    }
  }

  makeGridLines() {
    this.chartArea.selectAll('g.grid').remove();

    if (this.options.xGrid) {
      // add the X gridlines
      this.chartArea.append('g')
        .attr('class', 'grid')
        .attr('transform', 'translate(0,' + this.height + ')')
        .call(this.makeXGridlines()
          .tickSize(-this.height)
          .tickFormat(() => ''),
        );
    }

    if (this.options.yGrid) {
      // add the Y gridlines
      this.chartArea.append('g')
        .attr('class', 'grid')
        .call(this.makeYGridlines()
          .tickSize(-this.width)
          .tickFormat(() => ''),
        );
    }
  }

  findIndex(item, level, t?): number {
    let index = t ? this.preparedSeries[0].values.length - 1 : 0;
    let found = false;
    this.preparedSeries[0].values.forEach((value, i) => {
      if (value.label.slice(0, level + 1).join(DIVIDER) === item && (!found || t)) {
        index = i;
        found = true;
      }
    });
    return index;
  }

  getAttributeGroups(attributeIndex: number): IAttributeGroup[] {
    return this._response.data.reduce((acc: IAttributeGroup[], row: TTableValue[], j, rows): IAttributeGroup[] => {
      const key: string = row.slice(0, attributeIndex + 1).map(_ => String(_)).join(DIVIDER); // TODO: quote divider in attribute values
      const last = (j === rows.length - 1) || (row[attributeIndex] !== rows[j + 1][attributeIndex]);
      const existingGroupIndex = acc.findIndex(group => group.attr === key);
      if (existingGroupIndex === -1) {
        acc.push({
          level: attributeIndex,
          attr: key,
          start: this.attributeScale(this.findIndex(key, attributeIndex)),
          end: this.attributeScale(this.findIndex(key, attributeIndex, true)) + this.attributeScale.bandwidth(),
        });
      } else if (last) {
        acc[existingGroupIndex].end =
          this.attributeScale(this.findIndex(key, attributeIndex, true)) + this.attributeScale.bandwidth();
      }
      return acc;
    }, []);
  }

  refreshAxes(showCenterLine: boolean) {
    this.chartArea.selectAll('text').remove();
    this.chartArea.select('g.x-axis').remove();
    this.chartArea.select('g.y-axis').remove();
    this.chartArea.selectAll('g.chartGroup').remove();
    this.chartArea.selectAll('path.divider').remove();
    if (!this.preparedSeries.length) {
      return;
    }

    const attributeGroups: IAttributeGroup[] = this.config.attributes.reduce((acc, attr, index) => {
      acc.push(...this.getAttributeGroups(index));
      return acc;
    }, []);

    if (!('orientation' in this.options)
      || this.options.orientation === config.chart.options.orientation.values.VERTICAL) {
      const centerAxis = this.chartArea.select('g.center-line');
      // Center Line
      if (showCenterLine) {
        const center = d3.scaleLinear()
          .rangeRound([0, this.width]);
        const centerLine = d3.axisTop(center).ticks(0);
        const line = centerAxis.size()
          ? centerAxis
          : this.chartArea.append('g').attr('class', 'center-line');

        if (!centerAxis.size()) {
          line.call(centerLine).attr('transform', 'translate(0,' + this.height + ')');
        }
        line.call(centerLine).transition().duration(500).attr('transform', 'translate(0,' + this.metricScale(0) + ')');
      } else if (centerAxis.size()) {
        centerAxis.remove();
      }
      //X Axis + title
      if (this.config.options && 'hierarchy' in this.config.options && this.config.options.hierarchy) {
        const groups = this.chartArea.selectAll('.chartGroup')
          .data(attributeGroups);

        groups.enter()
          .append('g')
          .attr('class', 'chartGroup')
          .call((d) => this.drawVerticalGroups(d));

        const rects = this.chartArea.selectAll('g.chartGroup').selectAll('rect').data((d, i) => [attributeGroups[i]]);

        rects.call((d) => this.drawVerticalRects(d));

        rects.exit().remove();

        rects.enter()
          .append('rect')
          .attr('class', 'overlay')
          .call((d) => this.drawVerticalRects(d));

        const dividersStart = this.chartArea.selectAll('g.chartGroup').selectAll('path.divider1').data((d, i) => [attributeGroups[i]]);

        dividersStart.call((d) => this.drawVerticalDivider1(d));

        dividersStart.exit().remove();

        dividersStart.enter()
          .append('path')
          .attr('class', 'divider1')
          .call((d) => this.drawVerticalDivider1(d));

        const dividersEnd = this.chartArea.selectAll('g.chartGroup').selectAll('path.divider2').data((d, i) => [attributeGroups[i]]);

        dividersEnd.call((d) => this.drawVerticalDivider2(d));

        dividersEnd.exit().remove();

        dividersEnd.enter()
          .append('path')
          .attr('class', 'divider2')
          .call((d) => this.drawVerticalDivider2(d));

        const texts = this.chartArea.selectAll('g.chartGroup').selectAll('text').data((d, i) => [attributeGroups[i]]);

        texts.call((d) => this.drawVerticalText(d));

        texts.exit().remove();

        texts.enter()
          .append('text')
          .call((d) => this.drawVerticalText(d));

        const planks = this.chartArea.selectAll('g.chartGroup').selectAll('rect.plank').data((d, i) => [attributeGroups[i]]);

        planks.call((d) => this.drawVerticalPlank(d));

        planks.exit().remove();

        planks.enter()
          .append('rect')
          .attr('class', 'plank')
          .call((d) => this.drawVerticalPlank(d));

        // XAxis
        const xAxisOrdinal = d3.axisBottom(this.attributeScale)
          .tickValues([])
          .tickSizeOuter(offset);
        this.chartArea.append('g')
          .attr('class', 'x-axis')
          .attr('transform', `translate(0, ${this.height})`)
          .call(xAxisOrdinal);
      } else {
        //non hierarchy
        const xl = this.attributeScale.copy();
        xl.domain(d3.range(this.preparedSeries[0].values.length));
        const xAxis = d3.axisBottom(xl).ticks(10);
        this.chartArea.append('g')
          .attr('class', 'x-axis')
          .attr('transform', 'translate(0,' + this.height + ')')
          .call(xAxis)
          .selectAll<SVGTextElement, number>('text')
          .text((key) => {
            return this.preparedSeries[0].values[key].label.join(', ');
          })
          .attr('transform', `rotate(${this.options.xAxisAngle})`)
          .each(this._trimTextWrap(xl.bandwidth() * 2));
      }

      this.chartArea.append('text')
        .attr('text-anchor', 'middle')
        .attr('transform', `translate(${(this.width / 2)}, ${(this.height + this.margin.bottom - 5)})`)
        .text(this.options.xAxisTitle || (this.preparedSeries[0].label).join(', '))
        .each(this._trimTextWrap(this.width));

      const yAxis = d3.axisLeft(this.metricScale).tickFormat(this.metricScale.tickFormat(10)).ticks(10);
      this.chartArea.append('g')
        .attr('class', 'y-axis')
        .call(yAxis)
        .selectAll('text')
        .attr('transform', `rotate(${this.options.yAxisAngle})`);

      this.chartArea.append('text')
        .attr('text-anchor', 'middle')
        .attr('transform', `translate(0, -10)`)  // on top of left axis
        .text(this.options.yAxisTitle || this.preparedSeries.map(_ => _.column.displayName || _.column.name).join('/'))
        .each(this._trimTextWrap(this.margin.left * 2));
    } else if (this.options.orientation === config.chart.options.orientation.values.HORIZONTAL) {
      const centerAxis = this.chartArea.select('g.center-line');
      // Center Line
      if (showCenterLine) {
        const center = d3.scaleLinear()
          .rangeRound([0, this.height]);
        const centerLine = d3.axisRight(center).ticks(0);
        const line = centerAxis.size()
          ? centerAxis
          : this.chartArea.append('g').attr('class', 'center-line');

        if (!centerAxis.size()) {
          line.call(centerLine).attr('transform', 'translate(' + this.height + ', 0)');
        }
        line.call(centerLine).transition().duration(500).attr('transform', 'translate(' + this.metricScale(0) + ', 0 )');
      } else if (centerAxis.size()) {
        centerAxis.remove();
      }

      //X Axis + title
      if (this.config.options && 'hierarchy' in this.config.options && this.config.options.hierarchy) {
        const groups = this.chartArea.selectAll('g.chartGroup')
          .data(attributeGroups);

        groups.call((d) => this.drawHorizontalGroups(d));

        groups.exit().remove();

        groups.enter()
          .append('g')
          .attr('class', 'chartGroup')
          .call((d) => this.drawHorizontalGroups(d));

        const rects = this.chartArea.selectAll('g.chartGroup').selectAll('rect').data((d, i) => [attributeGroups[i]]);

        rects.call((d) => this.drawHorizontalRects(d));

        rects.exit().remove();

        rects.enter()
          .append('rect')
          .attr('class', 'overlay')
          .call((d) => this.drawHorizontalRects(d));

        const dividersStart = this.chartArea.selectAll('g.chartGroup').selectAll('path.divider1').data((d, i) => [attributeGroups[i]]);

        dividersStart.call((d) => this.drawHorizontalDivider1(d));

        dividersStart.exit().remove();

        dividersStart.enter()
          .append('path')
          .attr('class', 'divider1')
          .call((d) => this.drawHorizontalDivider1(d));

        const dividersEnd = this.chartArea.selectAll('g.chartGroup').selectAll('path.divider2').data((d, i) => [attributeGroups[i]]);

        dividersEnd.call((d) => this.drawHorizontalDivider2(d));

        dividersEnd.exit().remove();

        dividersEnd.enter()
          .append('path')
          .attr('class', 'divider2')
          .call((d) => this.drawHorizontalDivider2(d));

        const texts = this.chartArea.selectAll('g.chartGroup').selectAll('text').data((d, i) => [attributeGroups[i]]);

        texts.call((d) => this.drawHorizontalText(d));

        texts.exit().remove();

        texts.enter()
          .append('text')
          .call((d) => this.drawHorizontalText(d));

        const planks = this.chartArea.selectAll('g.chartGroup').selectAll('rect.plank').data((d, i) => [attributeGroups[i]]);

        planks.call((d) => this.drawHorizontalPlank(d));

        planks.exit().remove();

        planks.enter()
          .append('rect')
          .attr('class', 'plank')
          .call((d) => this.drawHorizontalPlank(d));

        const xAxisOrdinal = d3.axisLeft(this.attributeScale)
          .tickValues([])
          .tickSizeOuter(offset);
        this.chartArea.append('g')
          .attr('class', 'y-axis')
          .attr('transform', `translate(0, 0)`)
          .call(xAxisOrdinal);
      } else {
        //non hierarchy
        const xl = this.attributeScale.copy();
        xl.domain(d3.range(this.preparedSeries[0].values.length));
        const xAxis = d3.axisLeft(xl).ticks(10);
        this.chartArea.append('g')
          .attr('class', 'y-axis')
          .attr('transform', 'translate(0, 0)')
          .call(xAxis)
          .selectAll<SVGTextElement, number>('text')
          .text((key) => {
            return this.preparedSeries[0].values[key].label.join(', ');
          })
          .attr('transform', `rotate(${this.options.xAxisAngle})`)
          .each(this._trimTextWrap(/*xl.bandwidth()*/this.margin.left));
      }

      this.chartArea.append('text')
        .attr('text-anchor', 'middle')
        .attr('transform', `translate(${(this.width / 2)}, ${(this.height + 50)}) rotate(${this.options.yAxisAngle})`)
        .text(this.options.yAxisTitle || this.preparedSeries.map(_ => _.column.displayName || _.column.name).join('/'))
        .each(this._trimTextWrap(this.width));

      const yAxis = d3.axisBottom(this.metricScale).tickFormat(this.metricScale.tickFormat(10)).ticks(10);
      this.chartArea.append('g')
        .attr('class', 'x-axis')
        .attr('transform', 'translate(0,' + this.height + ')')
        .call(yAxis)
        .selectAll('text')
        .attr('transform', `rotate(${this.options.yAxisAngle})`)
        .each(this._trimTextWrap(this.attributeScale.bandwidth() * 2));

      this.chartArea.append('text')
        .attr('text-anchor', 'middle')
        .attr('transform', `translate(0, -10) rotate(${this.options.xAxisAngle})`)
        .text(this.options.xAxisTitle || this.preparedSeries[0].label.join(', '));
    }

    d3.selectAll('g.x-axis g.tick line')
      .each(function (_, i) {
        if ((i % 2)) {
          d3.select(this).attr('y2', 6);
          return;
        }
        d3.select(this).attr('y2', 26);
      });

    d3.selectAll('g.x-axis g.tick text')
      .each(function (_, i) {
        if ((i % 2)) {
          d3.select(this).attr('y', 8);
          return;
        }
        d3.select(this).attr('y', 28);
      });
  }

  private drawVerticalRects(d) {
    d.attr('x', (value) => {
      return value.start;
    })
      .attr('y', (value) => -(this.height + offset + shift * (this.config.attributes.length - value.level - 1)))
      .attr('width', (value) => value.end - value.start)
      .attr('height', (value) => this.height + offset + shift * (this.config.attributes.length - value.level));
  }

  private drawVerticalDivider1(d) {
    d.attr('d', (value) => {
      return `M${value.start},${shift * (value.level ? this.config.attributes.length - value.level - 1 : 1)}V${(-this.height - shift * (this.config.attributes.length - value.level - 1) - offset )}`;
    });
  }

  private drawVerticalDivider2(d) {
    d.attr('d', (value) => {
      return `M${value.end},${shift * (value.level ? this.config.attributes.length - value.level - 1 : 1)}V${(-this.height - shift * (this.config.attributes.length - value.level - 1 ) - offset)}`;
    });
  }

  private drawVerticalPlank(d) {
    //tslint:disable-next-line:no-this-assignment
    const self = this;
    d.attr('x', (value) => value.start)
      .attr('y', 0)
      .attr('width', (value) => value.end - value.start)
      .attr('height', '1em')
      .attr('fill', 'transparent')
      .on('mouseover', function (value) {
        if (value.level < self.config.attributes.length - 1) {
          d3.select((<any> this).parentNode).select('rect.overlay').style('fill', '#dcdcdc');
        }
      })
      .on('mouseleave', function (value) {
        if (value.level < self.config.attributes.length - 1) {
          d3.select((<any> this).parentNode).select('rect.overlay').style('fill', 'transparent');
        }
      });
  }

  private drawVerticalText(d) {
    //tslint:disable-next-line:no-this-assignment
    const self = this;
    d.text((value) => {
      return value.attr.split(DIVIDER)[value.level];
    }).attr('x', function (value) {
        const width = d3.select(this).node().getComputedTextLength();
        return (value.start + value.end - width) / 2;
      })
      .attr('y', '1em')
      .attr('text-anchor', 'start');

    //trim
    d.each(function (value) {
      return self._trimTextWrap(value.end - value.start).bind(this)();
    });
  }

  private drawVerticalGroups(d) {
    d.attr('class', 'chartGroup')
      .attr('transform', (value) => `translate(0, ${this.height + offset + shift * (this.config.attributes.length - value.level - 1)})`);
  }

  private drawHorizontalRects(d) {
    d.attr('y', (value) => value.start)
      .attr('x', (value) => -shift * (value.level ? this.config.attributes.length - value.level - 1 : 1))
      .attr('height', (value) => value.end - value.start)
      .attr('width', (value) => this.width + shift * (this.config.attributes.length - value.level));
  }

  private drawHorizontalDivider1(d) {
    d.attr('d', (value) => {
      return `M${-shift * (this.config.attributes.length - value.level - 1)},${value.start}H ${(this.width + shift * (this.config.attributes.length - value.level - 1))}`;
    });
  }

  private drawHorizontalDivider2(d) {
    d.attr('d', (value) => {
      return `M${-shift * (this.config.attributes.length - value.level - 1)},${value.end}H ${(this.width + shift * (this.config.attributes.length - value.level - 1))}`;
    });
  }

  private drawHorizontalPlank(d) {
    //tslint:disable-next-line:no-this-assignment
    const self = this;
    d.attr('x', function (value) {
      const textWidth = (<any> d3.select((<any> this).parentNode).select('text').node()).getComputedTextLength();
      return -shift * (self.config.attributes.length - value.level - 1) - 10 - textWidth;
    })
      .attr('y', (value) => value.start)
      .attr('height', (value) => value.end - value.start)
      .attr('width', function () {
        return (<any> d3.select((<any> this).parentNode).select('text').node()).getComputedTextLength();
      })
      .attr('fill', 'transparent')
      .on('mouseover', function (value) {
        if (value.level < self.config.attributes.length - 1) {
          d3.select((<any> this).parentNode).select('rect.overlay').style('fill', '#dcdcdc');
        }
      })
      .on('mouseleave', function (value) {
        if (value.level < self.config.attributes.length - 1) {
          d3.select((<any> this).parentNode).select('rect.overlay').style('fill', 'transparent');
        }
      });
  }

  private drawHorizontalText(d) {
    d.attr('y', function (value) {
      const width = d3.select(this).node().getComputedTextLength();
      return (value.start + value.end - width) / 2;
    })
      .attr('x', (value) => -shift * (this.config.attributes.length - value.level - 1) - 10)
      .attr('text-anchor', 'end')
      .attr('transform', `rotate(0)`)
      .text((value) => {
        return value.attr.split(DIVIDER)[value.level];
      });
  }

  private drawHorizontalGroups(d) {
    d.attr('transform', (value) => `translate(${0 - shift * (this.config.attributes.length - value.level - 1)}, 0)`);
  }
}

