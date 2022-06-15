import { DecimalPipe } from '@angular/common';
import {
  Component,
  EventEmitter,
  HostBinding,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import * as _ from 'lodash';
import { Observable } from 'rxjs/Observable';
import { delay } from 'rxjs/operators/delay';
import { switchMap } from 'rxjs/operators/switchMap';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { SaveToLibraryModalComponent } from '../core/components/save-to-library.modal.component';
import { IAsset, IBackendList, TObjectId } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { ProcessService } from '../core/services/process.service';
import { UserService } from '../core/services/user.service';
// TODO: remove circular modules dependency
import { IModelColumn, IModelHelpers } from '../train/model.interface';
import { ActivityObserver } from '../utils/activity-observer';
import { AppFormArray, AppFormGroup } from '../utils/forms';
import { ReactiveLoader } from '../utils/reactive-loader';

import { IDataset } from './dataset.interface';
import { ITable, ITableColumn, ITableColumnExt, ITableColumnStats, ITableStats } from './table.interface';
import { TableService } from './table.service';

// @todo Bug: moving between steps - sometimes datasets are appeared, sometimes they are disappeared
interface IDatasetRequest {
  tableId: TObjectId;
  value: any; //form params
}

type ColumnControlsFormGroup = AppFormGroup<{
  displayName: FormControl;
  name: FormControl;
  dataType: FormControl;
  variableType: FormControl;
  covariateType: FormControl;
}>;

@Component({
  selector: 'table-view-embed',
  providers: [
    TableService,
    DecimalPipe,
  ],
  template: `
    <app-spinner [visibility]="!table"></app-spinner>
    <div *ngIf="table" [ngSwitch]="table.status" class="flex-col">

      <process-indicator *ngSwitchCase="config.table.status.values.SAVING"
        [process]="tableProcesses[table.id]"></process-indicator>

      <error-indicator *ngSwitchCase="config.table.status.values.ERROR" [target]="'table'"
        [process]="tableProcesses[table.id]"></error-indicator>

      <div *ngSwitchCase="config.table.status.values.ACTIVE" class="flex-col">
        <app-spinner [visibility]="!tableDataset || (_datasetLoadingTooLong | async)"></app-spinner>
        <div class="p0 form-control brand-control flex-static">
          <div class="row">
            <div class="col-md-6">
              <div class="row">
                <div class="col-xs-8">
                  <div class="ellipsis text-bold">{{table.name}}</div>
                </div>
                <div class="col-xs-4">
                  <button *ngIf="!table.inLibrary"
                    class="btn btn-secondary btn-xs btn-right-small-device"
                    (click)="saveToLibrary()"
                    title="Save Table To The Library"
                  >
                    <i class="glyphicon glyphicon-book"></i>
                    <span class="full-text">Save To The Library</span>
                    <span class="small-text">Save</span>
                  </button>
                </div>
                </div>
            </div>
            <div class="col-md-6" *ngIf="tableDataset">
              <div class="pull-right">
                {{(tableDataset.count || 0) | pluralize:({other: '{} rows', '0': 'No rows', '1': '{} row'})}}
                <sort-columns *ngIf="!transpose" [columns]="table.columns | tableColumnSelectOptions"
                  [control]="datasetControlGroup.controls['order']"></sort-columns>
                <ng-template [ngIf]="columnOptions">
                  <button
                    type="button"
                    class="btn btn-xs btn-secondary"
                    [disabled]="!_someColumnsUnused"
                    title="Select All as Predictors"
                    (click)="selectAllPredictors()">
                    <i class="glyphicon glyphicon-ok"></i>
                  </button>
                  <button
                    type="button"
                    class="btn btn-xs btn-secondary"
                    [disabled]="!_somePredictorsSelected"
                    title="Remove All as Predictors"
                    (click)="deselectPredictors()">
                    <i class="glyphicon glyphicon-remove"></i>
                  </button>
                </ng-template>
                <button type="button" class="btn btn-xs btn-secondary" title="Transpose"
                  (click)="transpose = !transpose">
                  <i class="glyphicon glyphicon-list-alt"></i>
                </button>
                <button
                  *ngIf="showDownload"
                  class="btn btn-xs btn-secondary"
                  title="Download"
                  (click)="download()"
                >
                  <i class="glyphicon glyphicon-download-alt"></i>
                </button>
              </div>
              <div *ngIf="!transpose" class="pull-right">
                <app-pagination
                  [page]="datasetControlGroup.controls['page']"
                  [pageSize]="datasetControlGroup.controls['page_size']"
                  [currentPageSize]="tableDataset.data.length"
                  [rowsCount]="tableDataset.count">
                </app-pagination>
              </div>
            </div>
          </div>
        </div>
        <div [ngSwitch]="transpose" *ngIf="tableDataset" class="table-scroll flex-rubber">
          <table *ngSwitchCase="false" class="table table-dataset">
            <colgroup *ngIf="modelHelpers && !editMode">
              <col *ngFor="let item of table.columns; let i = index" [ngClass]="{
                  'response': item.name === modelHelpers.responseColumn?.name,
                  'predictor': item.name | apply: _predictorExists
                }"/>
            </colgroup>
            <colgroup *ngIf="editMode">
              <col *ngFor="let item of columnsControlGroup.controls" [ngClass]="{
                  'response':  item.controls.covariateType.value === config.model.column.covariate.values.RESPONSE,
                  'predictor': item.controls.covariateType.value === config.model.column.covariate.values.PREDICTOR
                }"/>
            </colgroup>
            <thead [ngSwitch]="editMode">
              <tr>
                <th class="text-center"
                  width="{{100 / table.columns.length}}%"
                  *ngFor="let item of table.columns"
                  tooltip
                  data-html="true"
                  data-placement="bottom"
                  data-container="body"
                  [tooltipTitle]="(item | apply: _isColumnNumeric) ? tableStatisticTooltips[item.name] : ''"
                  [grid-sort]="{ alias: item.name, name: item.displayName }"
                  [grid-sort-control]="datasetControlGroup.controls['order']">
                  {{item.displayName}}
                </th>
              </tr>
              <ng-template [ngSwitchCase]="true">
                <tr>
                  <th
                    *ngFor="let item of columnsControlGroup.controls; let i = index"
                    [ngSwitch]="table.columns[i] | apply: _isEditable"
                    class="text-muted tables-select-control">
                    <span *ngSwitchCase="false">{{table.columns[i] | apply: _getCovariateTitle}}</span>
                    <select *ngSwitchDefault [formControl]="item.controls.covariateType" class="form-control c-select">
                      <option [value]="null" disabled>- Select -</option>
                      <option *ngFor="let item of config.model.column.covariate.list" [value]="item">
                        {{ config.model.column.covariate.labels[item]}}
                      </option>
                    </select>
                  </th>
                </tr>
                <tr>
                  <th
                    *ngFor="let item of columnsControlGroup.controls; let i = index"
                    class="text-muted tables-select-control"
                    [ngSwitch]="table.columns[i] | apply: _isEditable: item.controls.covariateType.value"
                  >
                    <span *ngSwitchCase="false">{{table.columns[i] | apply: _getType: 'dataType'}}</span>
                    <select *ngSwitchDefault [formControl]="item.controls.dataType" class="form-control c-select">
                      <option [value]="null" disabled>- Select -</option>
                      <option *ngFor="let item of config.table.column.dataType.list" [value]="item">
                        {{ config.table.column.dataType.labels[item]}}
                      </option>
                    </select>
                  </th>
                </tr>
                <tr>
                  <th
                    *ngFor="let item of columnsControlGroup.controls; let i = index"
                    class="text-muted tables-select-control"
                    [ngSwitch]="table.columns[i] | apply: _isEditable: item.controls.covariateType.value"
                  >
                    <span *ngSwitchCase="false">{{table.columns[i] | apply: _getType: 'variableType'}}</span>
                    <select *ngSwitchDefault
                      [formControl]="item.controls.variableType"
                      class="form-control c-select">
                      <option [value]="null" disabled>- Select -</option>
                      <option *ngFor="let item of config.table.column.variableType.list" [value]="item">
                        {{ config.table.column.variableType.labels[item]}}
                      </option>
                    </select>
                  </th>
                </tr>
              </ng-template>

              <ng-template ngSwitchDefault="">
                <tr *ngIf="modelHelpers">
                  <th
                    *ngFor="let item of table.columns; let i = index"
                    class="text-muted">
                    {{item | apply: _getCovariateTitle}}
                  </th>
                </tr>

                <tr>
                  <th
                    *ngFor="let item of table.columns; let i = index"
                    class="text-muted">
                    {{item | apply: _getType: 'dataType'}}
                  </th>
                </tr>

                <tr>
                  <th
                    *ngFor="let item of table.columns; let i = index"
                    class="text-muted">
                    {{item | apply: _getType: 'variableType'}}
                  </th>
                </tr>
              </ng-template>
            </thead>
            <tbody>
              <tr *ngFor="let row of tableDataset.data; trackBy: _datasetTrackByFn">
                <td *ngFor="let value of row; let i = index"
                    [ngClass]="config.table.column.align.htmlClass[table.columns[i]?.align]"
                    [ngSwitch]="value !== null ? table.columns[i]?.dataType : null">
                  <ng-template [ngSwitchCase]="'${ITable.ColumnDataType.DOUBLE}'">
                    <span [title]="value | tableNumberTitle: '1.0-3'">
                      {{value | number: '1.0-3'}}
                    </span>
                  </ng-template>
                  <ng-template ngSwitchDefault>{{value | truncate:50}}</ng-template>
                </td>
              </tr>
            </tbody>
          </table>

          <table *ngSwitchCase="true" class="table table-dataset">
            <thead>
              <tr>
                <th class="text-center">Column Name</th>
                <th *ngIf="!!(columnOptions || modelHelpers)" class="text-center">Covariate Type</th>
                <th class="text-center">Data Type</th>
                <th class="text-center">Variable Type</th>
                <th class="text-center" *ngFor="let item of tableDatasetSample;let i = index">Data Row {{i+1}}</th>
              </tr>
            </thead>

            <tr *ngFor="let item of table.columns; let i = index" [ngClass]="{
                  'response':  (modelHelpers && item.name == modelHelpers.responseColumn?.name) || (editMode && columnsControlGroup.controls[i]['controls'].covariateType?.value === config.model.column.covariate.values.RESPONSE),
                  'predictor': (item.name | apply: _predictorExists) || (editMode && columnsControlGroup.controls[i]['controls'].covariateType?.value === config.model.column.covariate.values.PREDICTOR)
                }" [ngSwitch]="editMode">
              <th class="text-center link" style="font-weight: bold;">
                {{item.displayName}}
              </th>

              <ng-template [ngSwitchCase]="true">
                <th class="text-muted tables-select-control"
                  [ngSwitch]="item | apply: _isEditable"
                >
                  <span *ngSwitchCase="false">{{table.columns[i] | apply: _getCovariateTitle}}</span>
                  <select *ngSwitchDefault
                    [formControl]="columnsControlGroup.controls[i]['controls'].covariateType"
                    class="form-control c-select">
                      <option [value]="null" disabled>- Select -</option>
                      <option *ngFor="let item of config.model.column.covariate.list" [value]="item">
                        {{ config.model.column.covariate.labels[item]}}
                      </option>
                  </select>
                </th>

                <th class="text-muted tables-select-control"
                  [ngSwitch]="item | apply: _isEditable: columnsControlGroup.controls[i].controls.covariateType.value"
                >
                  <span *ngSwitchCase="false">{{item | apply: _getType: 'dataType'}}</span>
                  <select *ngSwitchDefault
                    [formControl]="columnsControlGroup.controls[i]['controls'].dataType"
                    class="form-control c-select">
                      <option [value]="null" disabled>- Select -</option>
                      <option *ngFor="let item of config.table.column.dataType.list" [value]="item">
                        {{ config.table.column.dataType.labels[item]}}
                      </option>
                  </select>
                </th>

                <th class="text-muted tables-select-control"
                  [ngSwitch]="item | apply: _isEditable: columnsControlGroup.controls[i].controls.covariateType.value"
                >
                  <span *ngSwitchCase="false">{{item | apply: _getType: 'variableType'}}</span>
                  <select *ngSwitchDefault
                    [formControl]="columnsControlGroup.controls[i]['controls'].variableType"
                    class="form-control c-select">
                      <option [value]="null" disabled>- Select -</option>
                      <option *ngFor="let item of config.table.column.variableType.list" [value]="item">
                        {{ config.table.column.variableType.labels[item]}}
                      </option>
                  </select>
                </th>
              </ng-template>

              <ng-template ngSwitchDefault="">
                <th *ngIf="modelHelpers" class="text-center text-muted">
                  {{item | apply: _getCovariateTitle}}
                </th>

                <th class="text-center text-muted">
                  {{item | apply: _getType: 'dataType'}}
                </th>

                <th class="text-center text-muted">
                  {{item | apply: _getType: 'variableType'}}
                </th>
              </ng-template>

              <td *ngFor="let row of tableDatasetSample"
                [ngClass]="config.table.column.align.htmlClass[item?.align]"
                [ngSwitch]="item?.dataType"
              >
                <ng-template [ngSwitchCase]="'${ITable.ColumnDataType.DOUBLE}'">
                  <span [title]="row[i] | tableNumberTitle: '1.0-3'">
                    {{row[i] | number: '1.0-3'}}
                  </span>
                </ng-template>
                <ng-template ngSwitchDefault>{{row[i] | truncate:50}}</ng-template>
              </td>
            </tr>
          </table>
        </div>
      </div>
    </div>
    <save-to-library-modal #saveToLibraryModal [service]="tableService"></save-to-library-modal>
  `,
})
export class TableViewEmbeddableComponent implements OnChanges, OnInit, OnDestroy {
  @Input() id: TObjectId;
  @Input() editMode: boolean = false;
  @Input() modelHelpers: IModelHelpers;
  @Input() columnOptions: ITableColumnExt[];
  @Input() lockedColumns: string[] = [];
  @Input() showDownload: boolean = false;
  @Output() columnOptionsChange: EventEmitter<ITableColumnExt[]> = new EventEmitter();
  @HostBinding('style.position') position = 'relative';
  @HostBinding('style.min-height') styleHeight = '100px';
  @HostBinding('class') classes = 'flex-col';
  readonly config = config;
  readonly tableProcesses: {[id: string]: IProcess};
  table: ITable;
  tableDataset: IBackendList<IDataset>;
  tableDatasetSample: IDataset[];
  tableStatisticTooltips: {[key: string]: string } = {};
  transpose: boolean = false;
  datasetControlGroup = new AppFormGroup({
    order: new FormControl(), // ordering
    page: new FormControl(1),
    page_size: new FormControl(config.table.view.pageSize.tableViewEmbed),
  });
  columnsControlGroup: AppFormArray<ColumnControlsFormGroup>;
  _somePredictorsSelected: boolean = false;
  _someColumnsUnused: boolean = false;
  readonly _datasetLoader: ReactiveLoader<IBackendList<IDataset>, IDatasetRequest>;
  readonly _datasetLoadingTooLong: Observable<boolean>;
  readonly _statisticLoader: ReactiveLoader<ITableStats, TObjectId>;
  readonly downloadObserver = new ActivityObserver();
  private datasetControlGroupDefaultValue: any;
  private subForTableStatistic: Subscription;
  private columnsFormSubscription: Subscription;
  private datasetFormSubscription: Subscription;
  private processSubscription: Subscription;
  private eventSubscription: Subscription;
  private decimalPipe: DecimalPipe = new DecimalPipe('en-US');
  @ViewChild('saveToLibraryModal') private saveToLibraryModal: SaveToLibraryModalComponent<ITable>;

  get tableService() {
    return this.tables;
  }

  constructor(
    private tables: TableService,
    private processes: ProcessService,
    private router: Router,
    private events: EventService,
    private userService: UserService,
  ) {
    this.tableProcesses = this.processes.data.targets[config.asset.aliasesPlural[config.asset.values.TABLE]];
    this.datasetControlGroupDefaultValue = this.datasetControlGroup.value;

    this._datasetLoader = new ReactiveLoader((data: IDatasetRequest) => this.tables.getDataset(data.tableId, data.value));
    this._datasetLoader.subscribe((data: IBackendList<IDataset>) => {
      this.tableDataset = data;
      this.tableDatasetSample = data.data.slice(0, 5);
    });

    this._datasetLoadingTooLong = this._datasetLoader.active.pipe(switchMap(active => {
      return Observable.of(active).pipe(delay(active ? 500 : 0));
    }));

    this._statisticLoader = new ReactiveLoader((id: TObjectId) => {
      // set numeric column tooltips to default message
      this.fillStatisticTooltips(config.tableStats.status.values.PENDING);
      return this.tables.getStatistic(id);
    });
    this._statisticLoader.subscribe((data: ITableStats) => {
      if (data.status === this.config.tableStats.status.values.ERROR) {
        this.fillStatisticTooltips(data.status);
        return;
      }
      // TODO: it's working when we have gone from route associated with this component. should it be deleted somehow?
      if (data.status === this.config.tableStats.status.values.PENDING) {
        this.subForTableStatistic && this.subForTableStatistic.unsubscribe();
        this.subForTableStatistic = this.processes.subscribeByTarget(data.id, IAsset.Type.TABLE_STATS, () => {
          this._statisticLoader.load(data.id);
        });
      } else {
        this.tableStatisticTooltips = data.stats.reduce((acc, cur: ITableColumnStats) => {
          acc[cur.columnName] = this._getTableColumnStatisticTooltipContent(cur);
          return acc;
        }, {});
      }
    });

    this.eventSubscription = this.events.subscribe((event) => {
      if (this.table && event.type === IEvent.Type.UPDATE_TABLE && this.table.id === event.data.id) {
        this.getTable();
      }
    });
  }

  ngOnChanges(changes: SimpleChanges) {
    // table change: reset stuff
    if (changes['id']
      && changes['id'].currentValue
      && changes['id'].currentValue !== changes['id'].previousValue
    ) {
      // get table
      this.getTable();
    } else if (changes['columnOptions']
      && changes['columnOptions'].currentValue
      && !_.isEqual(changes['columnOptions'].currentValue, changes['columnOptions'].previousValue)) {
      if (this.table) {
        this.initForm();
      }
    }
    this._predictorExists = (predictorName: string) => {
      return this.modelHelpers && !!this.modelHelpers.predictorColumns.find(_ => _.name === predictorName);
    };
    // update bound functions for pipes
    this._getCovariateTitle = (item: ITableColumn) => {
      if (this.editMode && this.columnOptions) {
        const data = this.columnOptions.find(_ => _.name === item.name);
        if (data) {
          return config.model.column.covariate.labels[data.covariateType];
        }
      }
      if (this.modelHelpers) {
        if (this.modelHelpers.predictedColumn && item.name === this.modelHelpers.predictedColumn) {
          return 'Predicted';
        }
        if (this.modelHelpers.probabilityColumns && this.modelHelpers.probabilityColumns.includes(item.name)) {
          return 'Probability';
        }
        if (this.modelHelpers.responseColumn && item.name === this.modelHelpers.responseColumn.name) {
          return config.model.column.covariate.labels[config.model.column.covariate.values.RESPONSE];
        }
        if (_.some(this.modelHelpers.predictorColumns || [], _ => _.name === item.name)) {
          return config.model.column.covariate.labels[config.model.column.covariate.values.PREDICTOR];
        }
      }
      return null;
    };

    this._getType = (item: ITableColumn, key: string) => { // or dataType
      if (this.editMode && this.columnOptions) {
        const data = this.columnOptions.find(_ => _.name === item.name);
        if (data) {
          return config.table.column[key].labels[data[key]] || 'Undefined';
        }
      }
      if (this.modelHelpers) {
        let index = this.findIndex(this.modelHelpers.predictorColumns, 'name', item.name);
        if (index > -1) {
          return this.modelHelpers.predictorColumns[index][key];
        }
        if (this.modelHelpers.responseColumn && this.modelHelpers.responseColumn.name === item.name) {
          return this.modelHelpers.responseColumn[key];
        }
      }
      return config.table.column[key].labels[item[key]] || 'Undefined';
    };

    this._isEditable = (item: ITableColumn, covariate?: string) => {
      if (this.lockedColumns.indexOf(item.name) >= 0) {
        return false;
      }

      //noinspection RedundantIfStatementJS
      if (covariate && covariate === config.model.column.covariate.values.IGNORE) {
        return false;
      }

      return true;
    };
  }

  ngOnInit() {
    // browse dataset (pagination/sorting)
    this.datasetFormSubscription = this.datasetControlGroup.valueChanges.subscribe(value => {
      if (this.table && this.table.status === this.config.table.status.values.ACTIVE) {
        this._datasetLoader.load({
          tableId: this.table.id,
          value: value,
        });
        this._statisticLoader.load(this.table.id);
      }
    });
  }

  ngOnDestroy() {
    this.eventSubscription && this.eventSubscription.unsubscribe();
    this.datasetFormSubscription && this.datasetFormSubscription.unsubscribe();
    this.columnsFormSubscription && this.columnsFormSubscription.unsubscribe();
    this.subForTableStatistic && this.subForTableStatistic.unsubscribe();
    this.processSubscription && this.processSubscription.unsubscribe();
  }

  saveToLibrary() {
    this.saveToLibraryModal.open(this.table);
  }

  _getCovariateTitle: (item: ITableColumn) => string = () => '';
  _getType: (item: ITableColumn, key: string) => string = () => '';
  _isEditable: (item: ITableColumn, covariate?: string) => boolean = () => false;
  _predictorExists: (predictorName: string) => boolean = () => false;

  newColumnControls(column: ITableColumn): ColumnControlsFormGroup {
    return new AppFormGroup({
      displayName: new FormControl(column.displayName, Validators.required),
      name: new FormControl(column.name, Validators.required),
      dataType: new FormControl(column.dataType, Validators.required),
      variableType: new FormControl(column.variableType, Validators.required),
      covariateType: new FormControl(),
    });
  }

  _isColumnNumeric(column: ITableColumn): boolean {
    switch (column.dataType) {
      case ITable.ColumnDataType.INTEGER:
      case ITable.ColumnDataType.DOUBLE:
      case ITable.ColumnDataType.LONG:
        return true;
      default:
        return false;
    }
  }

  _datasetTrackByFn = function(index) {
    return index;
  };


  selectAllPredictors() {
    this.initForm(this.columnOptions, config.model.column.covariate.values.PREDICTOR);
  }

  deselectPredictors() {
    this.initForm(this.columnOptions.filter(_ => _.covariateType !== config.model.column.covariate.values.PREDICTOR));
  }

  download(): void {
    if (!this.downloadObserver.isActive) {
      this.downloadObserver.observe(this.tables.download(this.id, this.userService.token()));
    }
  }

  private findIndex(arr: IModelColumn[], key: string, name: string): number {
    return arr.map(i => key in i && i[key]).indexOf(name);
  }

  private fillStatisticTooltips(status: string) {
    this.table && this.table.columns.map((col: ITableColumn) => {
      if (this._isColumnNumeric(col)) {
        this.tableStatisticTooltips[col.name] = config.tableStats.status.labels[status] || status;
      }
    });
  }

  private getTable() {
    this.table = undefined;
    this.tableDataset = undefined;
    this.tableDatasetSample = undefined;

    this.datasetControlGroup.reset(this.datasetControlGroupDefaultValue);

    this.tables.get(this.id).subscribe(table => {
      this.table = table;
      // get process (casual)
      let currentUrl = this.router.routerState.snapshot.url;
      if (table.status === this.config.table.status.values.SAVING) {
        this.processSubscription && this.processSubscription.unsubscribe();
        this.processSubscription = this.processes.subscribeByTarget(table.id, IAsset.Type.TABLE, () => {
          currentUrl === this.router.routerState.snapshot.url && this.getTable();
          this.tables.list();
        });
      }

      // get dataset
      if (table.status === this.config.table.status.values.ACTIVE) {
        this._datasetLoader.load({ tableId: this.table.id, value: this.datasetControlGroup.value });
        this._statisticLoader.load(this.table.id);
      }

      this.initForm();
    });
  }

  private _getTableColumnStatisticTooltipContent(tableColumnStatistic: ITableColumnStats): string {
    return `
      <table class='graph-info'>
        <tr><td>Name</td><td>${tableColumnStatistic.columnName}</td></tr>
        <tr><td>Min</td><td>${this.decimalPipe.transform(tableColumnStatistic.min, '1.0-3')}</td></tr>
        <tr><td>Max</td><td>${this.decimalPipe.transform(tableColumnStatistic.max, '1.0-3')}</td></tr>
        <tr><td>Avg</td><td>${this.decimalPipe.transform(tableColumnStatistic.avg, '1.0-3')}</td></tr>
        <tr><td>Std</td><td>${this.decimalPipe.transform(tableColumnStatistic.std, '1.0-3')}</td></tr>
        <!-- tr><td>Std Population</td><td>${tableColumnStatistic.stdPopulation}</td></tr -->
        <tr><td>Median</td><td>${this.decimalPipe.transform(tableColumnStatistic.median, '1.0-3')}</td></tr>
      </table>
    `;
  }

  private initForm(columnOptions: ITableColumnExt[] = this.columnOptions, defaultValue: string = config.model.column.covariate.values.IGNORE) {
    let columnsControlArray = new AppFormArray<ColumnControlsFormGroup>([]);

    this.table.columns.forEach(item => {
      const control = this.newColumnControls(item);

      if (this.editMode) {
        control.controls.covariateType.setValue(defaultValue);
        control.controls.covariateType.setValidators(Validators.required);
      }

      columnsControlArray.push(control);
    });

    // fill form with columnOptions
    if (columnOptions) {
      columnsControlArray.controls.forEach((group: FormGroup) => {
        const columnName = group.controls['name'].value;
        const newValue = columnOptions.find(value => value.name === columnName);
        if (newValue) {
          group.setValue(Object.assign(group.value, newValue));
        }
      });
    }

    this.columnsFormSubscription && this.columnsFormSubscription.unsubscribe();
    this.columnsFormSubscription = columnsControlArray.valueChanges
      .distinctUntilChanged()
      .subscribe(data => {
        this.columnOptionsChange.emit(data);
        this.updateFormDerivatives();
      });

    this.columnsControlGroup = columnsControlArray;
    this.columnOptionsChange.emit(columnsControlArray.value);
    this.updateFormDerivatives();
  }

  private updateFormDerivatives() {
    this._somePredictorsSelected = this.columnsControlGroup.controls.some((group: FormGroup) => {
      return group.controls['covariateType'].value === config.model.column.covariate.values.PREDICTOR;
    });
    this._someColumnsUnused = this.columnsControlGroup.controls.some((group: FormGroup) => {
      return group.controls['covariateType'].value === config.model.column.covariate.values.IGNORE;
    });
  }
}
