import { ElementRef, EventEmitter, OnDestroy, OnInit, Output, ViewChild } from '@angular/core';

import * as _ from 'lodash';
import 'rxjs/add/observable/forkJoin';
import 'rxjs/add/operator/mergeMap';
import { Observable } from 'rxjs/Observable';
import { Subject } from 'rxjs/Subject';

import config from '../config';
import { IAssetReference } from '../core/interfaces/common.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { IExperimentFull } from '../experiments/experiment.interfaces';
import { ExperimentService } from '../experiments/experiment.service';
import { ITable, ITableColumnStats } from '../tables/table.interface';
import { TableService } from '../tables/table.service';
import { ITabularTrainPipeline, ITabularTrainResult } from '../train/model.interface';
import { ModelService } from '../train/model.service';
import { MiscUtils } from '../utils/misc';
import { ReactiveLoader } from '../utils/reactive-loader';

import { DashboardCharts } from './charts/chart.interfaces';
import { CrossFilterBus } from './cross-filter-bus';
import { IDashboardWidget } from './dashboard.interface';
import { VisualizeDataService } from './visualize-data.service';
import { TabularAssetType, TabularDataRequest, TabularDataResponse } from './visualize.interface';

export abstract class ChartAbstract<T extends DashboardCharts.IChartOptions> implements OnInit, OnDestroy {
  public table: ITable;
  @Output() filtersChange = new EventEmitter<TabularDataRequest.Filter[]>();
  @ViewChild('canvas') protected selector: ElementRef;
  @ViewChild('filterEl') protected filtersBox: ElementRef;
  protected _response: TabularDataResponse; // data min max
  protected stats: ITableColumnStats[];
  protected _filters: {[key: string]: TabularDataRequest.Filter} = {};
  protected generators: {[key: string]: TabularDataRequest.Generator} = {};
  protected showFilters: boolean = true;
  protected boxHeight: number;
  protected boxWidth: number;
  protected _metaLoader: ReactiveLoader<[ITable, ITableColumnStats[]], IAssetReference>;
  protected _dataLoader: ReactiveLoader<TabularDataResponse, TabularDataRequest | null>;
  protected _dataRequests: Subject<TabularDataRequest> = new Subject<TabularDataRequest | null>();
  protected _rowsLimit: number;

  protected abstract defaultOptions: T;

  private _config: IDashboardWidget;
  private _crossFilters: TabularDataRequest.Filter[] = [];

  get options(): T {
    return Object.assign({}, this.defaultOptions, <T> this.config.options);
  }

  constructor(
    protected tables: TableService,
    protected models: ModelService,
    protected experimentService: ExperimentService,
    protected crossFilterBus: CrossFilterBus,
    private dataFetch: VisualizeDataService,
    private el: ElementRef,
    private events: EventService,
  ) {

    this._metaLoader = new ReactiveLoader((asset: IAssetReference) => {
      switch (asset.type) {
        case config.asset.values.MODEL:
          return this.models.get(asset.id).flatMap(model => {
            return model.experimentId
              ? this.experimentService.get(model.experimentId)
                .flatMap((experiment: IExperimentFull<ITabularTrainPipeline, ITabularTrainResult>) => {
                  return Observable.forkJoin(
                    this.tables.get(experiment.pipeline.input),
                    this.tables.getStatistic(experiment.pipeline.input).map(_ => _.stats),
                  );
                })
              : Observable.of(null);
          });
        case config.asset.values.TABLE:
          return Observable.forkJoin(
            this.tables.get(asset.id),
            this.tables.getStatistic(asset.id).map(_ => _.stats),
          );
        default:
          throw new Error('Unsupported asset type');
      }
    });

    this._metaLoader.subscribe(([table, stats]) => {
      this.table = table;
      this.stats = stats;
      this.refresh();
    });

    crossFilterBus.subscribe(items => {
      this._crossFilters = items.filter(_ => this.input && _.tableId === this.input.id).map(_ => _.filter);
      this._crossFilters.forEach(filter => this.setFilter(filter));
    });

    this._dataLoader = new ReactiveLoader((request) => this.dataFetch.fetchTabularData(request).debounceTime(0));

    this._dataLoader.subscribe(data => {
      if (!_.isEqual(data, this._response)) {
        this.visualizeData(this._response = data);
      }
    });

    MiscUtils.distinctUntilChangedDeep(this._dataRequests.asObservable()).debounceTime(300)
      .subscribe(request => this._dataLoader.load(request));
  }

  set config(value: IDashboardWidget) {
    if (!value) {
      return;
    }
    if (this._config && this._config.type !== value.type) {
      throw new Error('Can not change type of already initialized chart');
    }

    if (!('groups' in value)) {
      value.groups = [];
    }

    this._config = value;

    this._filters = this._config.chartFilters.reduce((acc, columnName) => {
      if (this._filters[columnName]) {
        acc[columnName] = this._filters[columnName];
      } else if (this._config.filters) {
        const configFilter = this._config.filters.find(_ => _.columnName === columnName);
        if (configFilter) {
          acc[columnName] = configFilter;
        }
      }
      return acc;
    }, {});
    this._crossFilters.forEach(filter => this._filters[filter.columnName] = Object.assign({}, filter));

    if (value.chartGenerators) {
      this.generators = this._config.chartGenerators.reduce((acc, generatorName) => {
        acc[generatorName] = this.generators[generatorName] || Object.assign({}, value.generators.find(_ => _.columnName === generatorName));
        return acc;
      }, {});
    } else {
      this.generators = {};
    }

    this.onInputChange();
    this.getData();
  }

  setFilter(filter: TabularDataRequest.Filter) {
    if (filter && !_.isEqual(filter, this._filters[filter.columnName])) {
      this._filters[filter.columnName] = filter;
      if (this.input && this.input.type === config.asset.values.TABLE) {
        this.crossFilterBus.push({
          tableId: this.input.id,
          filter: filter,
        });
      }
      this.filtersChange.emit(_.values(this._filters));
      this.getData();
    }
  }

  setGenerator(generator: TabularDataRequest.Generator) {
    if (generator && !_.isEqual(generator, this.generators[generator.columnName])) {
      this.generators[generator.columnName] = generator;
      this.getData();
    }
  }

  get config() {
    //return this._config;
    // TODO: fix edit-layout to use event emitter subscription instead
    return Object.assign(this._config, { filters: _.values(this._filters) });
  }

  get filters() {
    return this._filters;
  }

  protected get input(): IAssetReference<TabularAssetType> {
    return this._config ? this._config.input : null;
  }

  onInputChange() {
    if (!this.input) {
      return;
    }
    this._metaLoader.load(this.input);
  }

  getData() {
    if (!this.input || !this._config.metrics /*|| !this._config.attributes*/ || !this._config.metrics.length /*|| !this._config.attributes.length*/) {
      this._dataRequests.next(null);
      return;
    }

    const request: TabularDataRequest = {
      asset: this.input,
      filters: Object.keys(this._filters).map(_ => this._filters[_]),
      aggregations: (this._config.metrics || []),
      groupBy: this._config.attributes,
      groups: (this._config.groups || []),
      limit: this._rowsLimit,
    };

    if (this.input.type === config.asset.values.MODEL) {
      request.generators = this._config.generators.map(_ => {
        return this.generators[_.columnName] || _;
      });
    }

    this._dataRequests.next(request);
  }

  ngOnInit() {
    this.refresh();
  }

  ngOnDestroy(): void {
    this.selector && $(this.selector.nativeElement).empty();
  }

  abstract visualizeData(data: TabularDataResponse);

  toggleFilters(): void {
    this.showFilters = !this.showFilters;
    if (this.filtersBox) {
      $(this.filtersBox.nativeElement).toggle(this.showFilters);
      this.refresh();
    }
  }

  refresh(): void {
    let $el = $(this.el.nativeElement);
    this.boxWidth = $el.width();
    this.boxHeight = $el.height();

    if (!this.input) {
      return;
    }

    this.visualizeData(this._response);
  }

  download() {
    if (!this._response) {
      return;
    }
    const data = [this._response.columns.map(column => column.displayName), ...this._response.data]
      .map((row: string[]) => {
        return row.map((value) => {
          return `"${value.toString().replace(/"/g, '""')}"`;
        }).join(',');
      }).join('\n');
    const url = `data:application/octet-stream;charset=utf-8,` + encodeURIComponent(data);
    MiscUtils.downloadUrl(url, `${this.table.name}.csv`);
  }

  edit() {
    this.events.emit(IEvent.Type.CHART_EDIT, this.config.guid);
  }
}
