import { DecimalPipe } from '@angular/common';
import { Component, OnDestroy } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { Observable } from 'rxjs/Observable';
import { delay } from 'rxjs/operators/delay';
import { switchMap } from 'rxjs/operators/switchMap';
import { ISubscription } from 'rxjs/Subscription';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { ifMocks } from '../core/core.mocks-only';
import { IAsset, IBackendList, TObjectId } from '../core/interfaces/common.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { ProcessService } from '../core/services/process.service';
import { AclService } from '../services/acl.service';
import { ActivityObserver } from '../utils/activity-observer';
import { MiscUtils } from '../utils/misc';

import { IDataset } from './dataset.interface';
import { ITable, ITableColumn, ITableColumnStats, ITableStats } from './table.interface';
import { TableService } from './table.service';

@Component({
  selector: 'table-view',
  providers: [
    DecimalPipe,
  ],
  template: `
    <asset-operations [type]="config.asset.values.TABLE" [selectedItems]="[table]"
      (onDelete)="_onDeleteTable()"
      (onInfo)="_showStats = !_showStats"
    ></asset-operations>
    <app-spinner [visibility]="!table"></app-spinner>
    <div class="row">
      <div class="col-md-12 brand-tab">
        <div class="row">
          <div class="col-md-6">
            <app-input [label]="'Table Name'" [control]="tableEditForm.controls['name']"></app-input>
          </div>
          <div class="col-md-6">
            <div class="btn-group pull-right" role="group">
              <button type="button" class="btn btn-primary"
                [disabled]="tableEditForm.invalid || tableEditForm.pristine || tableEditForm.disabled || (_savingObserver.active | async)"
                (click)="saveTable()">
                Update&nbsp;<i class="glyphicon glyphicon-ok"></i></button>
            </div>
          </div>
        </div>
        <div class="row">
          <div class="col-md-6">
            <app-description [control]="tableEditForm.controls['description']"></app-description>
          </div>
        </div>
      </div>
    </div>
    <div *ngIf="table" class="row">
      <div *ngIf="_showStats" class="col-md-6 col-md-push-6">
        <app-spinner [visibility]="!_columnStats"></app-spinner>
        <div *ngIf="_columnStats" class="panel">
          <div class="panel-heading"><h3 class="panel-title">Statistics</h3></div>
          <div class="panel-body">
            <app-tabs *mocksOnly="true" [tabs]="['Column Statistics', 'Scatter Matrix']" [(active)]="activeStatsTab"></app-tabs>
            <div [hidden]="activeStatsTab !== 0">
              <app-select [label]="'Column'" [(value)]="_statsColumn"
                [options]="table.columns | tableColumnSelectOptions"></app-select>

              <ng-template [ngIf]="_statsColumn && _columnStats[_statsColumn]">
                <dl class="dl-horizontal" *ngIf="_statsColumn | apply: _findColumn | apply: _isColumnNumeric">
                  <dt>Min</dt>
                  <dd>{{_columnStats[_statsColumn].min | number: '1.0-3'}}</dd>
                  <dt>Max</dt>
                  <dd>{{_columnStats[_statsColumn].max | number: '1.0-3'}}</dd>
                  <dt>Average</dt>
                  <dd>{{_columnStats[_statsColumn].avg | number: '1.0-3'}}</dd>
                  <dt>Std. deviation</dt>
                  <dd>{{_columnStats[_statsColumn].std | number: '1.0-3'}}</dd>
                  <dt>Median</dt>
                  <dd>{{_columnStats[_statsColumn].median | number: '1.0-3'}}</dd>
                </dl>
                <canvas height="9" width="16"
                  [table-column]="_statsColumn | apply: _findColumn"
                  [table-column-histogram]="_columnStats[_statsColumn].histogram"
                ></canvas>
              </ng-template>
            </div>
            <div *mocksOnly="true" [hidden]="activeStatsTab !== 1">
              <table-scatter-plot-matrix
                *ngIf="activeStatsTab === 1"
                [table]="table"
              ></table-scatter-plot-matrix>
            </div>
          </div>
        </div>
      </div>
      <div [ngSwitch]="table.status" [ngClass]="_showStats ? 'col-md-6 col-md-pull-6' : 'col-md-12'">
        <process-indicator *ngSwitchCase="config.table.status.values.SAVING"
          [process]="processes.data['targets'].tables[table.id]"
        ></process-indicator>

        <error-indicator *ngSwitchCase="config.table.status.values.ERROR"
          [process]="processes.data['targets'].tables[table.id]" [target]="'table'"></error-indicator>

        <div *ngSwitchCase="config.table.status.values.ACTIVE">
          <app-spinner [visibility]="!tableDataset || (_datasetLoadingTooLong | async)"></app-spinner>
          <div class="row">
            <div class="col-md-12">
              <div class="p0 form-control brand-control">
                <div class="row">
                  <div class="col-md-6 ellipsis">
                    <strong>{{tableEditForm.controls['name'].value}}</strong>
                  </div>
                  <div class="col-md-6" *ngIf="tableDataset">
                    <div class="pull-right">
                      {{(tableDataset.count || 0) | pluralize:({other: '{} rows', '0': 'No rows', '1': '{} row'})}}
                      <sort-columns [columns]="table.columns | tableColumnSelectOptions"
                        [control]="tableViewForm.controls['order']"></sort-columns>
                    </div>
                    <div class="pull-right">
                      <app-pagination [page]="tableViewForm.controls['page']"
                        [pageSize]="tableViewForm.controls['page_size']"
                        [currentPageSize]="tableDataset.data.length"
                        [rowsCount]="tableDataset.count">
                      </app-pagination>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div *ngIf="tableDataset" [adaptiveHeight]="{minHeight: 450}" class="table-scroll">
            <!--{{tableViewForm.value | json}}-->
            <table class="table table-dataset table-bordered">
              <thead style="border-bottom: 2px gray solid;">
              <tr dnd-sortable-container [sortableData]="columnIndexes">
                <th class="text-center"
                  width="{{100 / table.columns.length}}%"
                  *ngFor="let columnIdx of columnIndexes; let i = index"
                  tooltip data-html="true" data-placement="bottom" data-container="body"
                  [tooltipTitle]="_isColumnNumeric(table.columns[columnIdx]) ? tableStatisticTooltips[table.columns[columnIdx].name] : ''"
                  dnd-sortable
                  [sortableIndex]="i"
                  (onDropSuccess)="onDropSuccess()"
                  (dragstart)="onDragStart($event)"
                  [grid-sort]="{ alias: table.columns[columnIdx].name, name: table.columns[columnIdx].displayName }"
                  [grid-sort-control]="tableViewForm.controls['order']">
                  {{table.columns[columnIdx].displayName}}
                </th>
              </tr>
              <tr>
                <th *ngFor="let columnIdx of columnIndexes" class="text-center text-muted">
                  {{config.table.column.dataType.labels[table.columns[columnIdx].dataType] || 'Undefined'}}
                </th>
              </tr>
              <tr>
                <th *ngFor="let columnIdx of columnIndexes" class="text-center text-muted tables-select-control"
                  [ngSwitch]="table.columns[columnIdx] | apply: _canChangeVariableType">
                  <span
                    *ngSwitchCase="false">{{config.table.column.variableType.labels[table.columns[columnIdx].variableType] || 'Undefined'}}</span>
                  <select *ngSwitchCase="true"
                    [formControl]="tableEditForm.controls['variableTypes']['controls'][columnIdx]"
                    class="form-control c-select">
                    <option [value]="null" disabled>- Select -</option>
                    <option *ngFor="let item of variableTypes" [value]="item.id">
                      {{ item.text }}
                    </option>
                  </select>
                </th>
              </tr>
              <tr *mocksOnly="true">
                <th *ngFor="let columnIdx of columnIndexes" class="text-center text-muted tables-select-control"
                  [ngSwitch]="table.columns[columnIdx] | apply: _canChangeColumnType">
                  <span
                    *ngSwitchCase="false">{{config.table.column.columnType.labels[table.columns[columnIdx].columnType] || 'Undefined'}}</span>
                  <select *ngSwitchCase="true"
                    [formControl]="tableEditForm.controls['columnTypes']['controls'][columnIdx]"
                    class="form-control c-select">
                    <option [value]="null" disabled>- Select -</option>
                    <option *ngFor="let item of columnTypes" [value]="item.id">
                      {{ item.text }}
                    </option>
                  </select>
                </th>
              </tr>
              </thead>
              <tbody>
              <tr *ngFor="let row of tableDataset.data; trackBy: _datasetTrackByFn">
                <td *ngFor="let columnIdx of columnIndexes"
                  [ngClass]="config.table.column.align.htmlClass[table.columns[columnIdx].align]"
                  [ngSwitch]="row[columnIdx] !== null ? table.columns[columnIdx].dataType : null">
                  <ng-template [ngSwitchCase]="'${ITable.ColumnDataType.DOUBLE}'">
                      <span [title]="row[columnIdx] | tableNumberTitle: '1.0-3'">
                        {{row[columnIdx] | number: '1.0-3'}}
                      </span>
                  </ng-template>
                  <ng-template ngSwitchDefault>{{row[columnIdx] | truncate:50}}</ng-template>
                </td>
              </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  `,
})
export class TableViewComponent implements OnDestroy {
  readonly config = config;
  readonly tableViewForm: FormGroup;
  readonly tableViewFormDefaultValue: any;
  readonly tableEditForm: FormGroup;
  readonly _savingObserver = new ActivityObserver();
  readonly _datasetLoadingTooLong: Observable<boolean>;
  table: ITable;
  tableDataset: IBackendList<IDataset>;
  columnIndexes: number[] = [];
  tableStatisticTooltips: {[key: string]: string } = {};
  variableTypes: AppSelectOptionData[] = AppSelectOptionData.fromList(
    config.table.column.variableType.list,
    config.table.column.variableType.labels,
  );
  columnTypes: AppSelectOptionData[] = AppSelectOptionData.fromList(config.table.column.columnType.list, config.table.column.columnType.labels);
  _statsColumn: string;
  _columnStats: {[p: string]: ITableColumnStats};
  _showStats: boolean = false;
  activeStatsTab: number = 0;
  private processSubscription: ISubscription;
  private eventsSubscription: ISubscription;
  private subForTableStatistic: ISubscription;
  private readonly viewFormSubscription: ISubscription;
  private readonly routeSubscription: ISubscription;
  private readonly decimalPipe: DecimalPipe = new DecimalPipe('en-US');
  private readonly _datasetLoadingObserver = new ActivityObserver();

  constructor(
    private route: ActivatedRoute,
    private tables: TableService,
    readonly processes: ProcessService,
    private router: Router,
    private acl: AclService,
    private events: EventService,
  ) {
    this.tableViewForm = new FormGroup({
      order: new FormControl(), // ordering
      page: new FormControl(1),
      page_size: new FormControl(config.table.view.pageSize.tableView),
    });
    this.tableViewFormDefaultValue = this.tableViewForm.value;

    this.tableEditForm = new FormGroup({
      name: new FormControl('', Validators.required),
      variableTypes: new FormArray([]),
      columnTypes: new FormArray([]),
      description: new FormControl(null),
    });

    // browse dataset (pagination/sorting)
    this.viewFormSubscription = this.tableViewForm.valueChanges.subscribe(() => {
      this.loadDataSet();
    });

    this.routeSubscription = this.route.params.subscribe(params => {
      this.loadTable(params['itemId']);
    });

    this.eventsSubscription = this.events.subscribe(event => {
      if (event.type === IEvent.Type.DELETE_TABLE && this.table.id === event.data.id) {
        this._onDeleteTable();
      }
    });

    this._datasetLoadingTooLong = this._datasetLoadingObserver.active.pipe(switchMap(active => {
      return Observable.of(active).pipe(delay(active ? 300 : 0));
    }));
  }

  onDragStart(event: DragEvent) {
    (<any> $(event.srcElement)).tooltip('hide');
  }

  ngOnDestroy() {
    this.viewFormSubscription.unsubscribe();
    this.routeSubscription.unsubscribe();
    this.processSubscription && this.processSubscription.unsubscribe();
    this.eventsSubscription && this.eventsSubscription.unsubscribe();
    this.subForTableStatistic && this.subForTableStatistic.unsubscribe();
  }

  saveTable(): void {
    const formValue = this.tableEditForm.value;

    const columns = this.table.columns.map((column, i) => {
      column.variableType = formValue.variableTypes[i];
      column.columnType = formValue.columnTypes[i];
      return column;
    });
    this._savingObserver.observe(this.tables.update(this.table.id, {
      name: this.tableEditForm.value.name,
      description: this.tableEditForm.value.description,
      columns: columns,
    })).subscribe((_) => this._setTable(_));
  }

  onDropSuccess() {
    // TODO: enable table update as backend handles it?
    // use this.columnDisplayIndexes to form columns sorting request
  }

  _onDeleteTable() {
    const currentProject = this.route.snapshot.params['projectId'];
    if (currentProject) {
      this.router.navigate(['/desk', 'library', 'projects', currentProject, 'tables']);
    } else {
      const navigateTo = ['/desk', 'library', 'tables'];
      const currentScope = this.route.snapshot.params['scope'];
      if (currentScope) {
        navigateTo.push(currentScope);
      }
      this.router.navigate(navigateTo);
    }
  }

  _canChangeColumnType = (tableColumn: ITableColumn) => {
    return config.table.column.dataType.columnTypes[tableColumn.dataType].length > 1;
  };

  _canChangeVariableType = (tableColumn: ITableColumn): boolean => {
    return ifMocks<boolean>(config.table.column.dataType.variableTypes[tableColumn.dataType].length > 1
      && this.table.datasetType === ITable.DatasetType.SOURCE, false);
  };

  _findColumn = (columnName: string): ITableColumn => {
    return this.table.columns.find(_ => _.name === columnName);
  };

  _isColumnNumeric = (column: ITableColumn): boolean => {
    switch (column.dataType) {
      case ITable.ColumnDataType.INTEGER:
      case ITable.ColumnDataType.DOUBLE:
      case ITable.ColumnDataType.LONG:
        return true;
      default:
        return false;
    }
  };

  _datasetTrackByFn = function(index) {
    return index;
  };

  private loadTable(id: TObjectId, silent?: boolean) {
    if ((this.table && id !== this.table.id) || !silent) {
      this.table = undefined;
      this.tableDataset = undefined;
    }
    // get table and dataset
    this.tables.get(id).subscribe((_) => this._setTable(_));
  }

  private _setTable(table: ITable) {
    // init view
    this.table = table;

    // reset column indexes
    this.columnIndexes = table.columns.map((_, i) => i);

    this.tableViewForm.reset(this.tableViewFormDefaultValue);

    // get process (casual)
    if (table.status === this.config.table.status.values.SAVING) {
      this.processSubscription = this.processes.subscribeByTarget(table.id, IAsset.Type.TABLE, () => {
        this.loadTable(table.id, true);
      });
    }

    this._loadTableStatistic();

    this.loadDataSet();

    // init form
    this.fillTablesForm(this.tableEditForm, table);
  }

  private _loadTableStatistic() {
    if ( !(this.table && this.table.status === this.config.table.status.values.ACTIVE) ) return;

    // set numeric column tooltips to default message
    this.fillStatisticTooltips(config.tableStats.status.values.PENDING);

    this.tables.getStatistic(this.table.id).subscribe((tableStatistic: ITableStats) => {
      if (tableStatistic.status === this.config.tableStats.status.values.ERROR) {
        this.fillStatisticTooltips(tableStatistic.status);
        return;
      }

      if (tableStatistic.status === this.config.tableStats.status.values.PENDING) {
        this.subForTableStatistic && this.subForTableStatistic.unsubscribe();
        this.subForTableStatistic = this.processes.subscribeByTarget(tableStatistic.id, IAsset.Type.TABLE_STATS, () => {
          this._loadTableStatistic();
        });
        return;
      }

      this.tableStatisticTooltips = tableStatistic.stats.reduce((acc, cur: ITableColumnStats) => {
        acc[cur.columnName] = this._getTableColumnStatisticTooltipContent(cur);
        return acc;
      }, {});

      this._columnStats = tableStatistic.stats.reduce((acc, cur: ITableColumnStats) => {
        acc[cur.columnName] = cur;
        return acc;
      }, {});
    });
  }

  private fillStatisticTooltips(status: string) {
    this.table && this.table.columns.map((col: ITableColumn) => {
      if (this._isColumnNumeric(col)) {
        this.tableStatisticTooltips[col.name] = config.tableStats.status.labels[status] || status;
      }
    });
  }

  private loadDataSet() {
    // get dataset
    if (this.table && this.table.status === this.config.table.status.values.ACTIVE && this.table.datasetId) {
      this._datasetLoadingObserver.observe(this.tables.getDataset(this.table.id, this.tableViewForm.value))
        .subscribe((dataset: IBackendList<IDataset>) => {
          this.tableDataset = dataset;
        });
    }
  }

  private fillTablesForm(form: FormGroup, table: ITable) {
    const canUpdate = this.acl.canUpdateTable(table);
    const tableUpdateFormData = {
      name: table.name,
      variableTypes: table.columns.map(column => column.variableType),
      columnTypes: table.columns.map(column => column.columnType),
      description: table.description,
    };
    MiscUtils.fillForm(form, tableUpdateFormData, !canUpdate);
    this.tableEditForm.markAsPristine();
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
}
