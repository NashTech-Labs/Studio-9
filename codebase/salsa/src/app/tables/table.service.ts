import { Injectable } from '@angular/core';

import 'rxjs/add/observable/empty';
import 'rxjs/add/observable/forkJoin';
import 'rxjs/add/operator/publish';
import { Observable } from 'rxjs/Observable';

import config from '../config';
import { mocksMode, mocksOnly } from '../core/core.mocks-only';
import {
  IAsset,
  IAssetListRequest,
  IAssetSaveParams,
  IAssetService,
  IBackendList,
  TObjectId,
} from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { DataService } from '../core/services/data.service';
import { EventService, IEvent } from '../core/services/event.service';
import { AppHttp } from '../core/services/http.service';
import { NotificationService } from '../core/services/notification.service';
import { ProcessService } from '../core/services/process.service';
import { SharedResourceService } from '../core/services/shared-resource.service';
import { MiscUtils } from '../utils/misc';

import { IDataset } from './dataset.interface';
import {
  IColumnValueParams,
  IDatasetParams,
  IScatterSummary,
  ITable,
  ITableClone,
  ITableId,
  ITableStats,
  ITableUpdate,
  TTableValue,
} from './table.interface';

export interface ITableImport {
  name?: string;
  format: string;
  delimiter?: string;
  nullValue?: string;
  description?: string;
}

export interface ITableCreate extends ITableImport {
  file: File;
}

@Injectable()
export class TableService extends DataService
  implements IAssetService<ITable, ITableCreate>,
    IAssetService.Cloneable<ITable, ITableClone>,
    IAssetService.Importable<ITable, ITableImport> {
  readonly assetType: IAsset.Type = IAsset.Type.TABLE;

  protected _type = IAsset.Type.TABLE;

  protected _datasetParams: IDatasetParams = {
    page: 1,
    page_size: 30,
  };

  constructor(
    protected http: AppHttp,
    protected events: EventService,
    private sharedService: SharedResourceService,
    private notifications: NotificationService,
    private processes: ProcessService,
  ) {
    super(events);
    this._data.listMeta = { count: 0, countPage: 0 };
  }

  list(params?: IAssetListRequest): Observable<IBackendList<ITable>> {
    // GET '/tables'
    const observable = this.http.get('tables', params, {
      deserialize: _ => this._patchTables(_),
    });

    return AppHttp.execute(observable,
      (data: IBackendList<ITable>) => {
        this._data.list = data.data || [];

        this._data.listMeta = {
          count: data.count,
          countPage: data.data.length,
        };
      },
    );
  }

  get(id: TObjectId): Observable<ITable> {
    // GET '/tables/:id'
    const observable = this.sharedService.withSharedAccess(
      IAsset.Type.TABLE,
      id,
    ).get('tables/' + id, null, {
      deserialize: _ => this._patchTable(_),
    });

    return AppHttp.execute(observable,
      (data: ITable) => {
        this._data.view = data;
        this._observables.view.next(data);
      },
    );
  }

  getActiveProcess(item: ITable): Observable<IProcess> {
    if (config.table.status.hasProcess[item.status]) {
      return this.processes.getByTarget(item.id, IAsset.Type.TABLE);
    } else {
      return Observable.of(null);
    }
  }

  getMany(ids: TObjectId[]): Observable<ITable[]> {
    // @TODO: need to have list endpoint extended
    const observable = Observable
      .forkJoin(...ids.map(id => {
        return this.sharedService.withSharedAccess(
          IAsset.Type.TABLE,
          id,
        ).get('tables/' + id, null, {
          deserialize: _ => this._patchTable(_),
          catchWith: () => null,
        });
      }))
      .map(tables => {
        return tables.filter(_ => _);
      })
      .share();

    return AppHttp.execute(observable);
  }

  update(tableId: TObjectId, data: ITableUpdate): Observable<ITable> {
    // PUT '/tables/:id'
    const observable = this.http.put(`tables/${tableId}`, data, null, {
      deserialize: _ => this._patchTable(_),
    });

    return AppHttp.execute(observable,
      (data: ITable) => {
        this._data.view = data;
        this._observables.view.next(data);

        this.events.emit(IEvent.Type.UPDATE_TABLE_LIST);
        this.events.emit(IEvent.Type.UPDATE_TABLE, { id: tableId });
        // RESERVED // this._data.list && this.list() // experimental // refresh the list
        this.notifications.create('Table updated: ' + data.name);
      },
    );
  }

  save(id: TObjectId, saveParams: IAssetSaveParams): Observable<ITable> {
    const observable = this.http.post('tables/' + id + '/save', saveParams);

    return AppHttp.execute(observable, () => {
      this.events.emit(IEvent.Type.UPDATE_TABLE_LIST);
      this.events.emit(IEvent.Type.UPDATE_TABLE, {id});
      this.notifications.create('Table saved.');
    });
  }


  'delete'(item: ITable): Observable<ITableId> {
    // DELETE '/tables/:id'
    const observable = this.http.delete('tables/' + item.id);

    return AppHttp.execute(observable,
      (data: ITableId) => {
        // update view item, if needed
        if (this._data.view && this._data.view.id === data.id) {
          this._data.view = null;
          this._observables.view.next(this._data.view);
        }

        this.events.emit(IEvent.Type.UPDATE_TABLE_LIST);
        this.events.emit(IEvent.Type.DELETE_TABLE, { id: item.id });
        this.notifications.create('Table deleted: ' + item.name);
      },
    );
  }

  getDataset(id: TObjectId, params: IDatasetParams = {}): Observable<IBackendList<IDataset>> {
    params = jQuery.extend(true, {}, this._datasetParams, params);

    // GET '/tables/:id/data'
    const observable = this.sharedService.withSharedAccess(
      IAsset.Type.TABLE,
      id,
    ).get('tables/' + id + '/data', params);

    return AppHttp.execute(observable);
  }

  create(params: ITableCreate) {
    const importParams: ITableImport = {
      name: params.name,
      format: params.format,
      delimiter: params.delimiter,
      nullValue: params.nullValue,
      description: params.description,
    };
    return this.import(params.file, importParams);
  }

  import(file: File, params: ITableImport): Observable<ITable> {
    const observable = this.http.monitoredUpload(`tables/import/${params.format}`, file, {
      name: params.name || file.name,
      nullValue: params.nullValue,
      delimiter: params.delimiter,
      description: params.description,
    }).flatMap((table: ITable) => this.get(table.id));

    return AppHttp.execute(observable,
      table => {
        this.events.emit(IEvent.Type.CREATE_TABLE, table);
        this.events.emit(IEvent.Type.UPDATE_TABLE_LIST);
      },
    );
  }

  exportUrl(id: TObjectId, token: string): Observable<string> {
    if (mocksMode) {
      return this.http.get(`tables/${id}/export`).map(_ => {
        return `data:application/octet-stream;charset=utf-8,` +
          encodeURIComponent(_);
      });
    }

    return this.sharedService.getSharedAccessParams(IAsset.Type.TABLE, id).map(data => {
      let url = `${config.api.base}tables/${id}/export?access_token=${token}`;

      if (data.shared_resource_id) {
        url += `&shared_resource_id=${data.shared_resource_id}`;
      }

      return url;
    });
  }

  download(id: TObjectId, token: string): Observable<boolean> {
    const observable = this.exportUrl(id, token).flatMap(url => {
      return MiscUtils.downloadUrl(url, `${id}.csv`);
    }).publish();

    observable.connect(); // make sure this runs irrespective of subscribers
    return observable;
  }

  clone(id: TObjectId, clone: ITableClone) {
    if (clone.name === '') {
      delete clone.name;
    }
    const observable = this.sharedService.withSharedAccess(
      IAsset.Type.TABLE,
      id,
    ).post('tables/' + id + '/copy', clone);

    return AppHttp.execute(observable,
      (table: ITable) => {
        this.events.emit(IEvent.Type.CREATE_TABLE, table);
        this.events.emit(IEvent.Type.UPDATE_TABLE_LIST);
        this.notifications.create('Table cloned: ' + table.name);
      },
    );
  }

  getStatistic(id: TObjectId): Observable<ITableStats> {
    const observable = this.sharedService.withSharedAccess(
      IAsset.Type.TABLE,
      id,
    ).get('tables/' + id + '/stats');

    return AppHttp.execute(observable);
  }

  values(id: TObjectId, params: IColumnValueParams): Observable<IBackendList<TTableValue>> {
    const observable = this.http.get('tables/' + id + '/values', params);
    return AppHttp.execute(observable);
  }

  @mocksOnly(Observable.empty())
  scatterSummary(id: TObjectId, binsCount?: number): Observable<IScatterSummary> {
    const observable = this.http.get(`tables/${id}/scatter-summary`, { binsCount });
    return AppHttp.execute(observable);
  }

  private _patchTables(tables: IBackendList<ITable>): IBackendList<ITable> {
    tables.data = tables.data.map(_ => this._patchTable(_));
    return tables;
  }

  private _patchTable(table: ITable): ITable {
    // mutating
    table.datasetType = <any> table.datasetType.toUpperCase();
    if (!table.datasetId && 'databaseId' in <any> table) {
      table.datasetId = (<any> table).databaseId;
    }
    if (!table.columns) {
      table.columns = [];
    } else {
      // mutating
      table.columns.forEach(column => {
        column.dataType = <any> column.dataType.toUpperCase();
        column.variableType = <any> column.variableType.toUpperCase();
        if (!column.columnType) {
          column.columnType = (column.variableType === ITable.ColumnVariableType.CONTINUOUS)
            ? config.table.column.columnType.values.METRIC
            : config.table.column.columnType.values.ATTRIBUTE;
        }
        if (!column.displayName) {
          column.displayName = column.name;
        }
        // fallback for old data
        if (String(column.variableType) === 'BOOLEAN') {
          column.variableType = ITable.ColumnVariableType.CATEGORICAL;
        }
      });
    }
    return table;
  }
}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      CREATE_TABLE = 'CREATE_TABLE',
      UPDATE_TABLE_LIST = 'UPDATE_TABLE_LIST',
      UPDATE_TABLE = 'UPDATE_TABLE',
      DELETE_TABLE = 'DELETE_TABLE',
    }
  }
}
