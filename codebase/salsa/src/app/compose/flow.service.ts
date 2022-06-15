import { Injectable } from '@angular/core';

import { Observable } from 'rxjs/Observable';

import config from '../config';
import { IAsset, IBackendList, IListRequest, IObjectId, TObjectId } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { DataService } from '../core/services/data.service';
import { EventService, IEvent } from '../core/services/event.service';
import { AppHttp } from '../core/services/http.service';
import { NotificationService } from '../core/services/notification.service';
import { ProcessService } from '../core/services/process.service';
import { SharedResourceService } from '../core/services/shared-resource.service';
import { ITable } from '../tables/table.interface';

import { IBackendFlow, IFlow, IFlowClone, IFlowCreate, IFlowInput, IFlowOutput, IFlowUpdate } from './flow.interface';
import { FlowstepService } from './flowstep.service';

@Injectable()
export class FlowService extends DataService {
  readonly assetType: IAsset.Type = IAsset.Type.FLOW;

  protected _type = IAsset.Type.FLOW;

  constructor(
    protected http: AppHttp,
    protected events: EventService,
    private sharedService: SharedResourceService,
    private notifications: NotificationService,
    private processes: ProcessService,
  ) {
    super(events);
    this._data.listMeta = { count: 0, countPage: 0 };
    // init refresh
    this.events
      .filter(e => e.type === IEvent.Type.PROCESS_COMPLETED)
      .subscribe(this._refreshExtra.bind(this));
  }

  list(params?: IListRequest): Observable<IBackendList<IFlow>> {
    // GET '/flows'
    const observable = this.http.get('flows', params, {
      deserialize: (response: IBackendList<IBackendFlow>) => {
        return {
          data: response.data.map(FlowService._deserializeFlow),
          count: response.count,
        };
      },
    });

    return AppHttp.execute(observable,
      (data: IBackendList<IFlow>) => {
        this._data.list = data.data;

        this._data.listMeta = {
          count: data.count,
          countPage: data.data.length,
        };

        // RESERVED // don't send "raw" data / need to think what to send...
        //this._observables.list.next(data);
      },
    );
  }

  // TODO: experimental / temporal. check/remove later
  getMyFlows(): Observable<IBackendList<IFlow>> {
    // GET '/api/me/flows'
    const observable = this.list({page: 1, page_size: 1000});
    return AppHttp.execute(observable,
      (data: IBackendList<IFlow>) => {
        this._data['flows'] = data.data;
        this.events.emit(IEvent.Type.UPDATE_USER_FLOWLIST, data);
      },
    );
  }

  get(id): Observable<IFlow> {
    // GET '/flows/:id'
    const observable = this.sharedService.withSharedAccess(
      IAsset.Type.FLOW,
      id,
    ).get('flows/' + id, null, {
      deserialize: FlowService._deserializeFlow,
    });

    return AppHttp.execute(observable,
      (data: IFlow) => {
        data.tables.forEach(tableId => {
          this.sharedService.setHierarchyLink(IAsset.Type.TABLE, tableId, IAsset.Type.FLOW, id);
        });

        this._data.view = data;
        this._observables.view.next(data);
      },
    );
  }

  getActiveProcess(item: IFlow): Observable<IProcess> {
    if (config.flow.status.hasProcess[item.status]) {
      return this.processes.getByTarget(item.id, IAsset.Type.FLOW);
    } else {
      return Observable.of(null);
    }
  }

  create(item?: IFlowCreate): Observable<IFlow> {
    item = item || this._data.edit;
    const data = jQuery.extend(true, {}, item);

    // POST '/flows'
    const observable = this.http.post('flows', data, null, {
      deserialize: FlowService._deserializeFlow,
    });

    return AppHttp.execute(observable,
      (data: IFlow) => {
        this._data.edit = data;
        this._observables.edit.next(data);

        this._data.list && this.list(); // experimental // refresh the list

        this.events.emit(IEvent.Type.CREATE_FLOW, data);
        this.events.emit(IEvent.Type.UPDATE_FLOW_LIST);
        this.notifications.create('Flow successfully created: ' + data.name);
      },
    );
  }

  update(item: IFlowUpdate): Observable<IFlow> {
    item = item || this._data.edit;
    if (!item.id) {
      return this.create(item as IFlowCreate);
    }
    const data = jQuery.extend(true, {}, item);

    // PUT '/flows/:id'
    const observable = this.http.put('flows/' + item.id, data, null, {
      deserialize: FlowService._deserializeFlow,
    });

    return AppHttp.execute(observable,
      (data: IFlow) => {
        this._data.view = data;
        this._observables.view.next(data);

        this.events.emit(IEvent.Type.UPDATE_FLOW, data.id);
        this.events.emit(IEvent.Type.UPDATE_FLOW_LIST);
        this.notifications.create('Flow updated: ' + data.name);
      },
    );
  }

  'delete'(item): Observable<IObjectId> {
    // DELETE '/flows/:id'
    const observable = this.http.delete('flows/' + item.id);

    return AppHttp.execute(observable,
      (data: IObjectId) => {
        // update view item, if needed
        if ((this._data.view || {}).id === data.id) {
          this._data.view = null;
          this._observables.view.next(this._data.view);
        }

        this._data.list && this.list(); // experimental // refresh the list

        this.events.emit(IEvent.Type.UPDATE_FLOW_LIST);
        this.events.emit(IEvent.Type.DELETE_FLOW, { id: item.id });
        this.notifications.create('Flow deleted: ' + item.name);
      },
    );
  }

  getTables(flowId: TObjectId, params?: any): Observable<IBackendList<ITable>> {
    // GET '/flows/:id/tables'
    const observable = this.sharedService.withSharedAccess(
      IAsset.Type.FLOW,
      flowId,
    ).get('flows/' + flowId + '/tables', params);

    return AppHttp.execute(observable,
      (data: IBackendList<ITable>) => {
        data.data.forEach(table => {
          this.sharedService.setHierarchyLink(IAsset.Type.TABLE, table.id, IAsset.Type.FLOW, flowId);
        });

        this._data.viewTables = data.data;
        this._refreshTables(this._data.viewTables);

        // RESERVED / this._observables.view.next(data);
      },
    );
  }

  addTables(id: TObjectId, tableId: TObjectId): Observable<IBackendList<ITable>> {
    // PUT '/flows/:id/tables/:tableId'
    const observable = this.http.put('flows/' + id + '/tables/' + tableId, {});

    return AppHttp.execute(observable,
      (data: IBackendList<ITable>) => {
        this._data.viewTables = data.data;
        this._refreshTables(this._data.viewTables);
        // RESERVED / this._observables.view.next(data);
        this.events.emit(IEvent.Type.UPDATE_FLOW_TABLES, id);
      },
    );
  }

  removeTables(flowId: TObjectId, tableId: TObjectId): Observable<IBackendList<ITable>> {
    // DELETE '/flows/:id/tables/:tableId'
    const observable = this.http.delete('flows/' + flowId + '/tables/' + tableId);

    return AppHttp.execute(observable,
      (data: IBackendList<ITable>) => {
        this._data.viewTables = data.data;
        this._refreshTables(this._data.viewTables);
        // RESERVED / this._observables.view.next(data);
        this.events.emit(IEvent.Type.UPDATE_FLOW_TABLES, flowId);
      },
    );
  }

  exportUrl(id: string, token: string): string {
    return config.api.base + 'flows/' + id + '/export' + '?access_token=' + token;
  }

  inputs(id: TObjectId): Observable<IFlowInput[]> {
    return this.sharedService.withSharedAccess(
      IAsset.Type.FLOW,
      id,
    ).get('flows/' + id + '/inputs');
  }

  outputs(id: TObjectId): Observable<IFlowOutput[]> {
    return this.sharedService.withSharedAccess(
      IAsset.Type.FLOW,
      id,
    ).get('flows/' + id + '/outputs');
  }

  clone(id: TObjectId, clone: IFlowClone) {
    if (clone.name === '') {
      delete clone.name;
    }
    const observable = this.sharedService.withSharedAccess(
      IAsset.Type.FLOW,
      id,
    ).post('flows/' + id + '/copy', clone);

    return AppHttp.execute(observable,
      (table: IFlow) => {
        this.events.emit(IEvent.Type.UPDATE_FLOW_LIST);
        this.notifications.create('Flow cloned: ' + table.name);
      },
    );
  }

  private _refreshExtra(event: IEvent): void {
    // TODO: add target checking (should be "table")
    let listTables = (this._data.viewTables || []).map(item => item.id);
    listTables.indexOf(event.data.targetId) > -1 && this._data.view && this.getTables(this._data.view.id);

    // TODO: add target checking (should be "flowstep")
    let listSteps = ((this._data.view || {}).steps || []).map(item => item.id);
    listSteps.indexOf(event.data.targetId) > -1 && this._data.view && this.getTables(this._data.view.id);
  }

  private _refreshTables(tables: ITable[]): void {
    // check/refresh "parent" flow object to preserve consistency (no api calls)
    this._data.view
    && this._data.viewTables.length !== tables.length
    && (this._data.viewTables = tables.map(item => item.id));
  }

  static _deserializeFlow(backendFlow: IBackendFlow): IFlow {
    return {
      id: backendFlow.id,
      ownerId: backendFlow.ownerId,
      name: backendFlow.name,
      description: backendFlow.description,
      created: backendFlow.created,
      updated: backendFlow.updated,
      status: backendFlow.status,
      steps: (backendFlow.steps || []).map(step => FlowstepService._deserializeFlowstep(step)),
      tables: backendFlow.tables,
      inLibrary: backendFlow.inLibrary,
    };
  }
}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      CREATE_FLOW = 'CREATE_FLOW',
      UPDATE_USER_FLOWLIST = 'UPDATE_USER_FLOWLIST',
      UPDATE_FLOW_LIST = 'UPDATE_FLOW_LIST',
      UPDATE_FLOW = 'UPDATE_FLOW',
      DELETE_FLOW = 'DELETE_FLOW',
      UPDATE_FLOW_TABLES = 'UPDATE_FLOW_TABLES',
    }
  }
}
