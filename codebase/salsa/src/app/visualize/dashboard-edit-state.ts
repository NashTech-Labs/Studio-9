import { Injectable } from '@angular/core';
import {
  FormControl,
  Validators,
} from '@angular/forms';

import * as _ from 'lodash';
import 'rxjs/add/observable/forkJoin';
import 'rxjs/add/observable/of';
import 'rxjs/add/operator/map';
import 'rxjs/add/operator/mergeMap';
import { Observable } from 'rxjs/Observable';

import config from '../config';
import { ModalService } from '../core-ui/services/modal.service';
import {
  IAsset,
  IAssetReference,
  TObjectId,
} from '../core/interfaces/common.interface';
import { IExperimentFull } from '../experiments/experiment.interfaces';
import { ExperimentService } from '../experiments/experiment.service';
import {
  ITable,
  ITableColumnStats,
} from '../tables/table.interface';
import { TableService } from '../tables/table.service';
import {
  ITabularModel,
  ITabularTrainModel,
  ITabularTrainPipeline,
  ITabularTrainResult,
} from '../train/model.interface';
import { ModelService } from '../train/model.service';
import {
  AppFormArray,
  AppFormGroup,
} from '../utils/forms';
import { MiscUtils } from '../utils/misc';
import { ReactiveLoader } from '../utils/reactive-loader';

import { DashboardCharts } from './charts/chart.interfaces';
import {
  IDashboard,
  IDashboardWidget,
} from './dashboard.interface';
import { TabularDataRequest } from './visualize.interface';

export interface IColumnInfo {
  name: string;
  displayName: string;
}

export interface IInputInfo extends IAssetReference {
  model?: ITabularTrainModel;
  table?: ITable;
  stats: ITableColumnStats[];
}

export type TWidgetFormGroup = AppFormGroup<{[K in keyof IDashboardWidget]: FormControl}>;

export class DashboardEditState {
  readonly form: AppFormGroup<{
    widgets: AppFormArray<TWidgetFormGroup>;
    name: FormControl,
    description: FormControl,
    layout: FormControl,
    inputs: FormControl,
    crossFilters: FormControl,
  }>;
  readonly dashboard: IDashboard = null;

  attrList: IColumnInfo[] = [];
  metrList: IColumnInfo[] = [];

  private _selectedWidget: number = null;
  private _previewInput: IAssetReference = null;

  private _inputsCache: IInputInfo[] = [];
  private _inputsLoader: ReactiveLoader<IInputInfo[], IAssetReference[]>;

  constructor(
    private tables: TableService,
    private models: ModelService,
    private experiments: ExperimentService,
    private modals: ModalService,
    dashboard?: IDashboard,
  ) {
    const widgetForm: TWidgetFormGroup = new AppFormGroup({
      input: new FormControl(null, Validators.required),
      type: new FormControl(null, Validators.required),
      name: new FormControl(null),
      chartFilters: new FormControl([]),
      filters: new FormControl([]),
      generators: new FormControl([]),
      chartGenerators: new FormControl([]),
      metrics: new FormControl([]),
      attributes: new FormControl([]),
      options: new FormControl(DashboardCharts.defaultChartOptions),
      guid: new FormControl(null, Validators.required),
      groups: new FormControl([]),
    });
    widgetForm.disable();
    this.form = new AppFormGroup({
      widgets: new AppFormArray<TWidgetFormGroup>([widgetForm], Validators.compose([Validators.required, Validators.minLength(1)])),
      name: new FormControl(null, Validators.required),
      description: new FormControl(),
      layout: new FormControl(null, Validators.required),
      inputs: new FormControl([], [Validators.required, Validators.minLength(1)]),
      crossFilters: new FormControl([]),
    });

    if (dashboard) {
      this.dashboard = dashboard;
      MiscUtils.fillForm(this.form, _.cloneDeep(dashboard));
    }

    this._inputsLoader = new ReactiveLoader((assets: IAssetReference[]): Observable<IInputInfo[]> => {
      if (!assets.length) {
        return Observable.of([]);
      }

      const currentAssets = this._inputsCache.filter(assetInfo => {
        return !!assets.find(_ => _.id === assetInfo.id && _.type === assetInfo.type);
      });

      const missingAssets = assets.filter(asset => {
        return !currentAssets.find(_ => _.id === asset.id && _.type === asset.type);
      });

      if (!missingAssets.length) {
        return Observable.of(currentAssets);
      }

      const missingObservables: Observable<IInputInfo[]>[] = _.chain(missingAssets)
        .groupBy('type')
        .map((typeAssets: IAssetReference[], type: string) => {
          const assetIds = typeAssets.map(_ => _.id);
          switch (type) {
            case config.asset.values.TABLE:
              return this.tables.getMany(assetIds).flatMap((tables: ITable[]) => {
                return Observable.forkJoin(...tables.map(table => {
                  return this.tables.getStatistic(table.id).map((stats): IInputInfo => {
                    return <IInputInfo> {
                      id: table.id,
                      type: config.asset.values.TABLE,
                      table: table,
                      stats: stats.stats,
                    };
                  });
                }));
              });
            case config.asset.values.MODEL:
              return this.models.getMany(assetIds).flatMap((models: ITabularModel[]) => {
                return Observable.forkJoin(...models.map(model => {
                  return model.experimentId
                    ? this.experiments.get(model.experimentId)
                      .flatMap((experiment: IExperimentFull<ITabularTrainPipeline, ITabularTrainResult>) => {
                        return this.tables.getStatistic(experiment.pipeline.input).map((stats): IInputInfo => {
                          return {
                            id: model.id,
                            type: IAsset.Type.MODEL,
                            model: {
                              ...model,
                              pipeline: experiment.pipeline as ITabularTrainPipeline,
                              result: experiment.result as ITabularTrainResult,
                            },
                            stats: stats.stats,
                          };
                        });
                      })
                    : Observable.of(null);
                }));
              });
          }
        })
        .value();

      return Observable.forkJoin(...missingObservables).map((items: IInputInfo[][]) => {
        return currentAssets.concat(...items);
      });
    });
    this._inputsLoader.subscribe((items: IInputInfo[]) => {
      this._inputsCache = items;
    });

    this.form.controls['inputs'].valueChanges.subscribe(inputs => {
      this._inputsLoader.load(inputs);
    });

    this._inputsLoader.load(this.form.controls['inputs'].value);
  }

  get ready(): boolean {
    return this._inputsLoader.loaded;
  }

  get widgetForm(): TWidgetFormGroup | null {
    if (this._selectedWidget !== null) {
      return this.form.controls['widgets'].controls[this._selectedWidget];
    }

    return null;
  }

  get previewInput() {
    return this._previewInput;
  }

  get modelList(): ITabularModel[] {
    return this._inputsCache.filter(_ => _.type === config.asset.values.MODEL).map(_ => _.model);
  }
  get tableList(): ITable[] {
    return this._inputsCache.filter(_ => _.type === config.asset.values.TABLE).map(_ => _.table);
  }

  getWidgetAttributeColumns(): IColumnInfo[] {
    return this.attrList.filter(column => this.widgetForm.value.attributes.includes(column.name));
  }

  getWidgetMetricColumns(): IColumnInfo[] {
    return this.metrList.filter(column => this.widgetForm.value.metrics.find(_ => _.columnName === column.name));
  }

  getPreviewAsset(): IAsset {
    if (!this._previewInput) {
      return null;
    }

    const assetInfo = this.findInputInfo(this._previewInput);
    if (!assetInfo) {
      return null;
    }

    switch (assetInfo.type) {
      case config.asset.values.TABLE:
        return assetInfo.table;
      case config.asset.values.MODEL:
        return assetInfo.model;
      default:
        throw new Error('Unsupported asset as input');
    }
  }

  setPreviewInput(value: IAssetReference) {
    this._selectedWidget = null;
    this._previewInput = value;
  }

  navigateBack() {
    this._selectedWidget = null;
    this._previewInput = null;
  }

  getWidgetIndexByGuid(guid: string): number {
    return this.form.value.widgets.findIndex(widget => widget.guid === guid);
  }

  selectWidget(i: number) {
    this._selectedWidget = i;
    this._previewInput = null;

    const widgetForm = this.widgetForm;

    if (widgetForm.value.input) {
      const inputInfo = this.findInputInfo((<IDashboardWidget> widgetForm.value).input);
      if (!inputInfo) {
        throw new Error('Input info not found in cache');
      }

      switch (inputInfo.type) {
        case config.asset.values.TABLE:
          this.setAttributesAndMetricsFromTable(inputInfo.table);
          break;
        case config.asset.values.MODEL:
          this.setAttributesAndMetricsFromModel(inputInfo.model);
          break;
        default:
          throw new Error('Unsupported asset as input');
      }
    }

  }

  generateWidget(asset: IAssetReference) {
    const inputInfo = this.findInputInfo(asset);
    if (!inputInfo) {
      throw new Error('Input info not found in cache');
    }

    switch (inputInfo.type) {
      case config.asset.values.TABLE:
        return this.generateWidgetByTable(inputInfo.table);
      case config.asset.values.MODEL:
        return this.generateWidgetByModel(inputInfo);
      default:
        throw new Error('Unsupported asset as input');
    }
  }

  determineBestChartType(config: IDashboardWidget): IDashboard.DashboardChartType {
    switch (true) {
      case config.metrics.length === 1 && config.metrics[0].aggregator === IDashboard.DashboardAggregationType.NO_AGGREGATE:
        return IDashboard.DashboardChartType.ONEDSCATTER;
      case config.attributes.length >= 3 && config.metrics.length <= 2:
        return IDashboard.DashboardChartType.SCATTER;
      case config.attributes.length === 2 && config.metrics.length >= 2 && config.metrics.length <= 3:
        return IDashboard.DashboardChartType.SCATTER;
    }
    return IDashboard.DashboardChartType.BAR;
  }

  setWidgetTable(tableId: TObjectId) {
    const inputInfo = this.findInputInfo({
      id: tableId,
      type: IAsset.Type.TABLE,
    });
    if (!inputInfo) {
      throw new Error('Input info not found in cache');
    }

    this.setAttributesAndMetricsFromTable(inputInfo.table);
    this.widgetForm.patchValue(this.generateWidgetByTable(inputInfo.table));
  }

  setWidgetModel(modelId: TObjectId): void {
    const inputInfo = this.findInputInfo({
      id: modelId,
      type: IAsset.Type.MODEL,
    });
    if (!inputInfo) {
      throw new Error('Input info not found in cache');
    }

    this.setAttributesAndMetricsFromModel(inputInfo.model);
    this.widgetForm.patchValue(this.generateWidgetByModel(inputInfo));
  }

  isCurrentTable(tableId: string): boolean {
    return this.isCurrentAsset({id: tableId, type: IAsset.Type.TABLE});
  }

  isCurrentModel(modelId: string): boolean {
    return this.isCurrentAsset({id: modelId, type: IAsset.Type.MODEL});
  }

  isCurrentAsset(asset: IAssetReference): boolean {
    if (this._previewInput) {
      return _.isEqual(this._previewInput, asset);
    }
    if (this.widgetForm && (<IDashboardWidget> this.widgetForm.value).input) {
      return _.isEqual(this.widgetForm.value.input, asset);
    }

    return false;
  }

  getCurrentInput(): IInputInfo | null {
    if (this.widgetForm && (<IDashboardWidget> this.widgetForm.value).input) {
      return this.findInputInfo(this.widgetForm.value.input);
    }

    return null;
  }

  removeInput(input: IAssetReference) {
    this.navigateBack();

    const value = (<IDashboard> this.form.value);
    const inputIsUsed = _.some(value.widgets || [], _ => {
      return _.input.id === input.id && _.input.type === input.type;
    });

    if (inputIsUsed) {
      this.modals.alert('First remove all charts using this input');
      return;
    }

    const newInputs = value.inputs.filter(_ => {
      return _.id !== input.id || _.type !== input.type;
    });

    this.form.controls['inputs'].setValue(newInputs);
  }

  setMetric(columnName: string, aggregator?: IDashboard.DashboardAggregationType): void {
    const metrics = (<IDashboardWidget> this.widgetForm.value).metrics.filter(_ => _.columnName !== columnName);
    if (aggregator) {
      metrics.push({ columnName, aggregator });
    }
    this.widgetForm.controls.metrics.setValue(metrics);
    // fix chart type to best one
    const bestType = this.determineBestChartType(this.widgetForm.value);
    this.widgetForm.controls['type'].setValue(bestType);
  }

  toggleAttribute(attrName: string, checked: boolean): void {
    if (!this.widgetForm) {
      return;
    }
    const attributes = (<IDashboardWidget> this.widgetForm.value).attributes.filter(_ => _ !== attrName);
    if (checked) {
      attributes.push(attrName);
    }
    this.widgetForm.controls.attributes.setValue(attributes);
    // fix chart type to best one
    const bestType = this.determineBestChartType(this.widgetForm.value);
    this.widgetForm.controls['type'].setValue(bestType);
  }

  private findInputInfo(input: IAssetReference): IInputInfo {
    return this._inputsCache.find(_ => {
      return _.id === input.id && _.type === input.type;
    });
  }

  private generateWidgetByTable(table: ITable): Partial<IDashboardWidget> {
    //const attributes = table.columns.filter(column => column.columnType === config.table.column.columnType.values.ATTRIBUTE);
    //const metrics = table.columns.filter(column => column.columnType === config.table.column.columnType.values.METRIC);

    return {
      type: IDashboard.DashboardChartType.BAR,
      name: this._uniqueWidgetName(table.name),
      input: { id: table.id, type: IAsset.Type.TABLE },
      metrics: [], /*metrics.slice(0, 2).map(_ => {
        return <TabularDataRequest.Aggregation> {
        aggregator: IDashboard.DashboardAggregationType.SUM,
        columnName: _.name,
        };
      }),*/
      attributes: [], /*attributes.slice(0, 1).map(_ => _.name),*/
      filters: [],
      generators: [],
      chartGenerators: [],
      chartFilters: [],
    };
  }

  private generateWidgetByModel(input: IInputInfo): Partial<IDashboardWidget> {
    const generators = input.model.predictorColumns.map((column): TabularDataRequest.Generator => {
      const isCategorical = column.variableType !== ITable.ColumnVariableType.CONTINUOUS;
      const stats = input.stats.find(_ => _.columnName === column.name);

      if (isCategorical) {
        return {
          columnName: column.name,
          type: 'categorical',
          values: ('mostFrequentValue' in stats) ? [stats.mostFrequentValue] : [],
        };
      } else {
        const {min, max} = stats;
        return {
          columnName: column.name,
          type: 'continuous',
          min: min,
          max: max,
          steps: 1,
        };
      }
    });

    return {
      type: IDashboard.DashboardChartType.BAR,
      name: this._uniqueWidgetName(input.model.name),
      input: { id: input.model.id, type: IAsset.Type.MODEL },
      metrics: [], /*[input.model.responseColumn].map(_ => {
        return <TabularDataRequest.Aggregation> {
          aggregator: IDashboard.DashboardAggregationType.AVG,
          columnName: _.name,
        };
      }),*/
      attributes: [], /*input.model.predictorColumns.slice(0, 1).map(_ => _.name),*/
      filters: [],
      generators: generators,
      chartGenerators: [],
      chartFilters: [],
    };
  }

  private setAttributesAndMetricsFromTable(table: ITable): void {
    this.attrList = table.columns.filter(column => column.columnType === config.table.column.columnType.values.ATTRIBUTE);
    this.metrList = table.columns.filter(column => column.columnType === config.table.column.columnType.values.METRIC);
  }

  private setAttributesAndMetricsFromModel(model: ITabularModel): void {
    this.attrList = model.predictorColumns;
    this.metrList = [model.responseColumn];
  }

  private _uniqueWidgetName(desiredName: string): string {
    const currentWidgetNames = ((<IDashboard> this.form.value).widgets || []).map(_ => _.name);
    let seqId = 0;
    let uniqueName = desiredName;

    while (currentWidgetNames.find(_ => _ === uniqueName)) {
      seqId++;
      uniqueName = `${desiredName} #${seqId}`;
    }

    return uniqueName;
  }
}

@Injectable()
export class DashboardEditStateFactory {
  constructor(
    private tables: TableService,
    private models: ModelService,
    private experiments: ExperimentService,
    private modals: ModalService,
  ) {}

  createInstance(dashboard?: IDashboard) {
    return new DashboardEditState(this.tables, this.models, this.experiments, this.modals, dashboard);
  }
}
