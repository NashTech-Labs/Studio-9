import { Component, ElementRef, HostBinding, NgZone } from '@angular/core';

import * as d3 from 'd3';
import * as d3Chromatic from 'd3-scale-chromatic';
import * as _ from 'lodash';
import { NumericDictionary } from 'lodash';
import 'rxjs/add/observable/forkJoin';
import { Observable } from 'rxjs/Observable';
import * as topojson from 'topojson';

import config from '../../config';
import { EventService } from '../../core/services/event.service';
import { ExperimentService } from '../../experiments/experiment.service';
import { TTableValue } from '../../tables/table.interface';
import { TableService } from '../../tables/table.service';
import { ModelService } from '../../train/model.service';
import { CrossFilterBus } from '../cross-filter-bus';
import { GeoDataService, IUSLocationRow, IUsStateCounty } from '../geodata.service';
import { VisualizeDataService } from '../visualize-data.service';
import { TabularDataRequest, TabularDataResponse } from '../visualize.interface';

import { DashboardCharts } from './chart.interfaces';
import { D3Chart } from './d3-chart.component';

const MIN_BUBBLE_RADIUS = 2;
const FONT_SIZE = 10;
const BUBBLE_LEGEND_PADDING = 5;

interface IPreparedZipData {
  values: number[];
  USObject: IUSLocationRow;
  radius: number;
}

interface IPreparedTopologyData {
  values: number[];
  USObject: IUsStateCounty;
  radius: number;
}

interface IPreparedPathTopologyData {
  value: number;
  USObject: IUsStateCounty;
}

@Component({
  selector: 'geo-heat-chart',
  template: `
    <div class="flex-col vertical-align" style="width: 100%"
      *ngIf="!config.attributes.length || !config.metrics.length">
      <div class="text-center" style="width: 100%">
        <a (click)="edit()" style="font-size: 50px"><i class="glyphicon glyphicon-pencil"></i>Edit</a>
      </div>
    </div>
    <div class="flex-rubber" [hidden]="!config.attributes.length || !config.metrics.length">
      <div class="svg" style="width: 100%; height: 100%;">
        <svg #canvas width="960" height="500" stroke-linejoin="round" stroke-linecap="round">
          <defs>
            <pattern id="checkers"
              x="0" y="0" width="12" height="12" patternUnits="userSpaceOnUse" >
              <rect fill="#eee" x="0" width="6" height="6" y="0"/>
              <rect fill="#eee" x="6" width="6" height="6" y="6"/>
            </pattern>
            <filter id="blur">
              <feGaussianBlur stdDeviation="5"></feGaussianBlur>
            </filter>
            <clipPath id="cut-off-circle">
              <rect x="-55" y="-13" width="500" height="26" />
            </clipPath>
          </defs>
        </svg>
      </div>
    </div>
    <div #filterEl [hidden]="!showFilters" class="chart-filters flex-static"
      *ngIf="(config?.chartFilters?.length + config?.chartGenerators?.length)"
      style="position: relative; width: 25%; padding-left: 5px; border-left: 1px solid #C0C0C0;"
    >
      <app-spinner [visibility]="_metaLoader.active | async"></app-spinner>
      <ng-template [ngIf]="_metaLoader.loaded">
        <chart-filter *ngFor="let columnName of config.chartFilters"
          [value]="filters[columnName]"
          [stats]="stats"
          [table]="table"
          [columnName]="columnName"
          (valueChange)="setFilter($event)"></chart-filter>
        <chart-generator *ngFor="let columnName of config.chartGenerators"
          [value]="generators[columnName]"
          [stats]="stats"
          [table]="table"
          [columnName]="columnName"
          (valueChange)="setGenerator($event)"></chart-generator>
      </ng-template>
    </div>
  `,
  styles: [`
    @media (min-width: 992px) {
      .chart-filters {
        width: 25%;
        padding-left: 5px;
        border-left: 1px solid #C0C0C0;
      }
    }
  `],
})
export class GeoHeatMapComponent extends D3Chart<DashboardCharts.IGeoHeatMapOptions> {
  @HostBinding('class') classes = 'row-flex';
  @HostBinding('style.height') styleHeight = '100%';
  readonly defaultOptions = DashboardCharts.defaultGeoHeatMapOptions;
  private _scale: number;
  private _rule: number = 0;
  private _defs: d3.Selection<SVGElement, any, any, any>;

  constructor(tables: TableService,
              models: ModelService,
              experiments: ExperimentService,
              bus: CrossFilterBus,
              dataFetch: VisualizeDataService,
              el: ElementRef,
              events: EventService,
              private geoData: GeoDataService,
              private zone: NgZone) {
    super(tables, models, experiments, bus, dataFetch, el, events);
  }

  visualizeData(data: TabularDataResponse) {
    super.visualizeData(data);

    if (!this.width || !this.height || !this.svgHeight || !this.svgWidth || !data) {
      return;
    }

    const width = this.svgWidth - this.margin.left - this.margin.right;
    const height = this.svgHeight - this.margin.top - this.margin.bottom;
    this._scale = Math.min(height / 600, width / 960);

    this.chartArea
      .attr('transform', `translate(${this.margin.left + (width - 960 * this._scale) / 2},${this.margin.top + (height - 600 * this._scale) / 2}) scale(${this._scale})`);

    if (!data || !data.data || !data.data[0]) {
      return;
    }
    this.chartArea.selectAll('.paths').remove();
    this.chartArea.selectAll('.bubbles').remove();
    this.chartArea.selectAll('.counties-mesh').remove();
    this.chartArea.selectAll('.country-mesh').remove();
    this.chartArea.selectAll('.states-mesh').remove();

    this._defs = this.svg.select('defs');

    const metricIndexes: number[] = this.config.metrics.map(_ => data.columns.findIndex(column => column.name === _.columnName));
    const metricLabels: string[] = this.config.metrics.map((metric: TabularDataRequest.Aggregation) => {
      const column = data.columns.find(column => column.name === metric.columnName);
      return `${metric.aggregator}(${column.displayName || metric.columnName})`;
    });

    if (this.options.geoType === config.chart.options.geoType.values.COUNTY) {
      Observable.forkJoin(this.geoData.getUSMap(), this.geoData.getCountyNames()).subscribe(([us, counties]) => {
        const stateIndex = data.columns.findIndex(column => column.name === this.options.stateColumn);
        const countyIndex = data.columns.findIndex(column => column.name === this.options.countyColumn);

        const series: d3.Map<number[]> = d3.map();
        const countiesInverted = _.keyBy(counties, county => {
          return `${county.code}|${county.name}`;
        });
        data.data.forEach((row: TTableValue[]) => {
          if (row[stateIndex] && row[countyIndex]) {
            const key: string = `${row[stateIndex]}|${row[countyIndex]}`;
            const county: IUsStateCounty = countiesInverted[key];

            if (county) {
              series.set(String(county.id), metricIndexes.map(_ => +row[_]));
            } else {
              //console.log(row);
            }
          }
        });

        const path: d3.GeoPath<any, any> = d3.geoPath();

        this._drawCountryContours(path, us);
        if (this.options.drawMethod === DashboardCharts.DrawMethod.BUBBLE) {
          this._drawCountiesContours(path, us);
          this._drawTopologiesBubble(topojson.feature(us, us.objects.counties).features, path, series, counties, metricLabels);
        } else {
          this._drawTopologiesPath(topojson.feature(us, us.objects.counties).features, path, series, counties, metricLabels);
          this._drawStateContours(path, us);
        }
      });
    } else if (this.options.geoType === config.chart.options.geoType.values.STATE) {
      Observable.forkJoin(this.geoData.getUSMap(), this.geoData.getStateNames()).subscribe(([us, states]) => {
        const stateIndex = data.columns.findIndex(column => column.name === this.config.attributes[0]);
        const series: d3.Map<number[]> = d3.map();
        const field = 'code';
        const statesInverted = _.keyBy(states, state => {
          return state[field];
        });
        data.data.forEach((row: TTableValue[]) => {
          if (row[stateIndex]) {
            const state: IUsStateCounty = statesInverted[row[stateIndex]];
            if (state) {
              series.set(String(state.id), metricIndexes.map(_ => +row[_]));
            } else {
              //console.log(row);
            }
          }
        });
        const path = d3.geoPath();
        this._drawCountryContours(path, us);
        this._drawStateContours(path, us);
        if (this.options.drawMethod === DashboardCharts.DrawMethod.BUBBLE) {
          this._drawTopologiesBubble(topojson.feature(us, us.objects.states).features, path, series, states, metricLabels);
        } else {
          this._drawTopologiesPath(topojson.feature(us, us.objects.states).features, path, series, states, metricLabels);
        }
      });
    } else {
      Observable.forkJoin(this.geoData.getUSMap(), this.geoData.getZIPCoords()).subscribe(([us, usCommonData]) => {
        const series: d3.Map<number[]> = d3.map();
        data.data.forEach(row => {
          const usObject = usCommonData[row[0]];
          if (usObject) {
            series.set(usObject.zip_code, metricIndexes.map(_ => +row[_]));
          }
        });
        const path = d3.geoPath();
        this._drawCountryContours(path, us);
        this._drawStateContours(path, us);

        this._drawZipBubble(usCommonData, series, metricLabels);
      });
    }

    this.zone.runOutsideAngular(() => {
      const zoom = d3.zoom()
        .scaleExtent([0.5, 10])
        .on('zoom', this._zoomed.bind(this));
      this.svg.call(zoom);
    });
  }

  private _gamma(arg: number): string {
    switch (this.options.gamma) {
      case DashboardCharts.Gamma.BLUES:
        return d3Chromatic.interpolateBlues(arg);
      case DashboardCharts.Gamma.GREENS:
        return d3Chromatic.interpolateGreens(arg);
      case DashboardCharts.Gamma.REDS:
        return d3Chromatic.interpolateReds(arg);
      case DashboardCharts.Gamma.GREYS:
        return d3Chromatic.interpolateGreys(arg);
      case DashboardCharts.Gamma.MAGMA:
        return d3.interpolateMagma(arg);
      case DashboardCharts.Gamma.RAINBOW:
        return d3Chromatic.interpolateSpectral(arg);
      case DashboardCharts.Gamma.RED_BLUE:
        return d3Chromatic.interpolateRdBu(arg);
    }
  }

  private _drawTopologiesPath(
    topologies: any[],
    path: d3.GeoPath<any, any>,
    series: d3.Map<number[]>,
    dictionary: NumericDictionary<IUsStateCounty>,
    metricLabels: string[],
  ): void {
    const colorScale: d3.ScaleLinear<number, number> = d3.scaleLinear()
      .range([0, 1])
      .domain(d3.extent(series.values(), _ => _[0]))
      .clamp(true);

    const preparedData: IPreparedPathTopologyData[] = topologies
      .map((t): IPreparedPathTopologyData => {
        const value = series.has(`${+t.id}`)
          ? series.get(`${+t.id}`)[0]
          : null;
        return Object.assign({
          value: value,
          USObject: dictionary[`${+t.id}`],
        }, t);
      });

    this.chartArea
      .append('g')
      .attr('class', 'paths')
      .selectAll('path')
      .data(preparedData)
      .enter()
      .append('path')
      .attr('fill', (row: IPreparedPathTopologyData) => {
        return row.value !== null
          ? this._gamma(colorScale(row.value))
          : 'url(#checkers)';
      })
      .attr('d', path)
      .append('title')
      .text((row: IPreparedPathTopologyData) => {
        const value: string = row.value === null
          ? 'N/A'
          : String(row.value);
        return row.USObject ? `${row.USObject.name}, ${row.USObject.code}: ${value}` : '';
      });

    this._legend([colorScale], metricLabels);
  }

  private _drawTopologiesBubble(
    topologies: any[],
    path: d3.GeoPath<any, any>,
    series: d3.Map<number[]>,
    dictionary: NumericDictionary<IUsStateCounty>,
    metricLabels: string[],
  ): void {
    const sizeScale: d3.ScaleLinear<number, number> = d3.scaleLinear()
      .range([MIN_BUBBLE_RADIUS, +this.options.bubbleSize])
      .domain([0, d3.max([0, ...series.values().map(_ => _[0])])])
      .clamp(true);
    const colorScale: d3.ScaleLinear<number, number> = (this.config.metrics.length >= 2)
      ? d3.scaleLinear().range([0, 1]).domain(d3.extent(series.values(), _ => _[1]))
      : null;
    const opacityScale = d3.scaleLinear()
      .domain(sizeScale.domain())
      .range([0.8, 0.2]);

    const preparedData: IPreparedTopologyData[] = topologies
      .filter((t: any) => series.has(`${+t.id}`) && `${+t.id}` in dictionary)
      .map(t => {
        const values = series.get(`${+t.id}`);
        return Object.assign({
          values: values,
          USObject: dictionary[`${+t.id}`],
          radius: sizeScale(values[0]),
        }, t);
      });

    this.chartArea
      .append('g')
      .attr('class', 'bubbles')
      .selectAll('circle')
      .data(<IPreparedTopologyData[]> preparedData)
      .enter()
      .append('circle')
      .attr('transform', (row: IPreparedTopologyData) => {
        const centroid = path.centroid(row);
        return centroid ? `translate(${centroid})` : '';
      })
      .attr('r', (row: IPreparedTopologyData) => {
        return row.radius + 'px';
      })
      .attr('fill-opacity', (row) => opacityScale(row.values[0]))
      .attr('fill', (row: IPreparedTopologyData) => {
        return this._gamma(colorScale ? colorScale(row.values[1]) : 1.0);
      })
      .append('title')
      .text((row: IPreparedTopologyData) => {
        return `${row.USObject.name} ` + metricLabels.map((label, i) => `${label}: ${row.values[i]}`).join(', ');
      });

    this._legend([sizeScale, colorScale], metricLabels);
  }

  private _drawZipBubble(
    commonData: NumericDictionary<IUSLocationRow>,
    series: d3.Map<number[]>,
    metricLabels: string[],
  ): void {
    const sizeScale: d3.ScaleLinear<number, number> = d3.scaleLinear()
      .range([MIN_BUBBLE_RADIUS, +this.options.bubbleSize])
      .domain([0, d3.max([0, ...series.values().map(_ => _[0])])])
      .clamp(true);
    const colorScale: d3.ScaleLinear<number, number> = (this.config.metrics.length >= 2)
      ? d3.scaleLinear().range([0, 1]).domain(d3.extent(series.values(), _ => _[1]))
      : null;

    const projection = d3.geoAlbersUsa().scale(1280);
    const opacityScale = d3.scaleLinear()
      .domain(sizeScale.domain())
      .range([0.8, 0.2]);
    const data: IPreparedZipData[] = series.keys().filter(key => key in commonData).map((key: string) => {
      const values = series.get(key);
      return {
        values: values,
        USObject: commonData[key],
        radius: sizeScale(values[0]),
      };
    });
    this.chartArea
      .append('g')
      .attr('class', 'bubbles')
      .attr('transform', `translate(0, 50)`)
      .selectAll('circle')
      .data(data)
      .enter()
      .append('circle')
      .attr('transform', (row: IPreparedZipData) => {
        const center = projection([row.USObject.longitude, row.USObject.latitude]);
        return center ? `translate(${center})` : '';
      })
      .attr('r', (row) => {
        return row.radius + 'px';
      })
      .attr('fill-opacity', (row) => opacityScale(row.values[0]))
      .attr('fill', (row: IPreparedZipData) => {
        return this._gamma(colorScale ? colorScale(row.values[1]) : 1.0);
      })
      .append('title')
      .text((row: IPreparedZipData) => {
        return `Zip: ${row.USObject.zip_code} State: ${row.USObject.stateName} ` +
          metricLabels.map((label, i) => `${label}: ${row.values[i]}`).join(', ');
      });

    this._legend([sizeScale, colorScale], metricLabels);
  }

  private _legend(scales: d3.ScaleLinear<number, number>[], metricLabels: string[]): void {
    const legendWidth = this.svgWidth / 4;
    const h = 20;
    this.svg.selectAll('rect.legend').remove();
    this.svg.select('.legend-axis').remove();
    this.svg.selectAll('.label').remove();
    this.svg.selectAll('.legend-circle-label').remove();
    this.svg.selectAll('.legend-circle').remove();
    this.svg.selectAll('.titles').remove();

    //Title
    this.svg.append('text')
      .attr('x', (this.svgWidth / 2))
      .attr('y', 15)
      .attr('text-anchor', 'middle')
      .attr('class', 'titles')
      .style('font-size', '16px')
      .style('text-decoration', 'underline')
      .text(this.options.title);
    //SubTitle
    this.svg.append('text')
      .attr('x', (this.svgWidth / 2))
      .attr('y', 30)
      .attr('text-anchor', 'middle')
      .attr('class', 'titles')
      .style('font-size', FONT_SIZE + 'px')
      .text(this.options.subtitle);

    const legendRightMostX = this.svgWidth - this.margin.right + this.potentialLegendWidth;

    if (this.options.drawMethod === DashboardCharts.DrawMethod.BUBBLE || this.options.geoType === DashboardCharts.GeoHeatMapType.ZIP) {
      const [valueMin, valueMax] = scales[0].domain();
      const data = [valueMin, (valueMax - valueMin) / 2, valueMax];

      const selection = this.svg.append('g');
      selection.append('rect').attr('transform', `translate(${this.svgWidth - 100}, 20)`);
      const circles = selection.attr('class', 'legend-circle').selectAll('circle')
        .data(data)
        .enter();
      const bubbleSize = this.options.bubbleSize * this._scale;

      circles.append('circle').attr('clip-path', 'url(#cut-off-circle)')
        .attr('transform', (_, i) => {
          return `translate(${legendRightMostX - bubbleSize},  ${40 + i * (25 + BUBBLE_LEGEND_PADDING)})`;
        })
        .attr('r', (value: number) => {
          const radius = scales[0](value) || MIN_BUBBLE_RADIUS;
          return radius ? radius * this._scale + 'px' : MIN_BUBBLE_RADIUS * this._scale + 'px';
        })
        .attr('fill', this._gamma(1.0));

      circles.append('text')
        .attr('class', 'label')
        .style('text-anchor', 'end')
        .style('fill', 'black')
        .style('font-size', FONT_SIZE + 'px')
        .attr('alignment-baseline', 'middle')
        .attr('y', (value, i) => 40 + i * (25 + BUBBLE_LEGEND_PADDING))
        .attr('x', legendRightMostX - BUBBLE_LEGEND_PADDING - (bubbleSize * 2))
        .text((d) => {
          return this.decimalPipe.transform(parseFloat(String(d)), '1.0-1');
        });
      this.svg.append('text').attr('class', 'label')
        .attr('transform', `translate(${legendRightMostX}, 10)`)
        .style('text-anchor', 'end')
        .text(metricLabels[0]);
    }

    if (this.config.metrics.length >= 2
      || (this.options.drawMethod === DashboardCharts.DrawMethod.AREA && this.options.geoType !== DashboardCharts.GeoHeatMapType.ZIP)) {

      const colorMetricIndex = scales.length > 1 ? 1 : 0;

      this._defs.select(`#gradient-${this.config.guid}-${this._rule}`).remove();
      this._rule++;
      const legend = this._defs.append('svg:linearGradient')
        .attr('id', `gradient-${this.config.guid}-${this._rule}`)
        .attr('spreadMethod', 'pad');
      for (let i = 1; i <= 10; i++) {
        legend.append('stop').attr('offset', `${i * 10}%`).attr('stop-color', this._gamma(i / 10)).attr('stop-opacity', 1);
      }

      this.svg.append('rect')
        .attr('class', 'legend')
        .attr('width', legendWidth)
        .attr('height', h).style('fill', `url(#gradient-${this.config.guid}-${this._rule})`).attr('transform', 'translate(10,10)');

      const x = d3.scaleLinear().range([0, legendWidth]).domain(scales[colorMetricIndex].domain());

      const xAxis = d3.axisBottom(x).tickFormat(x.tickFormat(4)).ticks(4);

      this.svg.append('g').attr('class', 'legend-axis').attr('transform', 'translate(10,30)').call(xAxis);
      this.svg.append('text').attr('class', 'label').attr('transform', `translate(${legendWidth + 20}, ${h + 5})`).style('text-anchor', 'start')
        .text(metricLabels[colorMetricIndex]);
    }
  }

  private _drawCountryContours(path: d3.GeoPath<any, any>, us: any) {
    this.chartArea.append('path')
      .data(topojson.feature(us, us.objects.nation).features)
      .attr('class', 'country-shape')
      .attr('d', path);
  }


  private _drawCountiesContours(path: d3.GeoPath<any, any>, us: any) {
    this.chartArea.append('path')
      .datum(topojson.mesh(us, us.objects.counties, (a, b) => {
        return a !== b;
      }))
      .attr('class', 'counties-mesh')
      .attr('d', path);
  }

  private _drawStateContours(path: d3.GeoPath<any, any>, us: any) {
    this.chartArea.append('path')
      .datum(topojson.mesh(us, us.objects.states, (a, b) => {
        return a !== b;
      }))
      .attr('class', 'states-mesh')
      .attr('d', path);
  }

  private _zoomed(): void {
    this.chartArea.attr('transform', `translate(${d3.event.transform.x}, ${d3.event.transform.y}) scale(${this._scale * d3.event.transform.k})`);
    this.chartArea.selectAll('g.bubbles circle').each((_, index, nodeList) => {
      d3.select(nodeList[index]).attr('r', (value: IPreparedZipData | IPreparedTopologyData) => {
        return value.radius / d3.event.transform.k + 'px';
      });
    });
  }
}
