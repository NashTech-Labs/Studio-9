import { Injectable } from '@angular/core';

import { Observable } from 'rxjs/Observable';

import { IAsset, IAssetListRequest, IAssetService, TObjectId } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { DataService } from '../core/services/data.service';
import { EventService, IEvent } from '../core/services/event.service';
import { AppHttp } from '../core/services/http.service';
import { NotificationService } from '../core/services/notification.service';
import { ProcessService } from '../core/services/process.service';
import { SharedResourceService } from '../core/services/shared-resource.service';

import { IOptimization, IOptimizationCreate, IOptimizationId, IOptimizationUpdate } from './optimization.interface';

@Injectable()
export class OptimizationService extends DataService implements IAssetService<IOptimization, IOptimizationCreate> {
  readonly assetType: IAsset.Type = IAsset.Type.OPTIMIZATION;

  protected _type = IAsset.Type.OPTIMIZATION;

  constructor(
    protected http: AppHttp,
    protected events: EventService,
    private sharedService: SharedResourceService,
    private notifications: NotificationService,
    private processes: ProcessService,
  ) {
    super(events);
    this._data.listMeta = {count: 0, countPage: 0};
  }

  list(params?: IAssetListRequest): Observable<any> {
    const observable = this.http.get('optimizations', params);

    return AppHttp.execute(observable,
      (data: any) => {
        this._data.list = data.data || [];

        this._data.listMeta = {
          count: data.count,
          countPage: data.data.length,
        };

        this._observables.list.next(data);
      },
    );
  }

  get(id: TObjectId): Observable<IOptimization> {
    const observable = this.sharedService.withSharedAccess(
      IAsset.Type.OPTIMIZATION,
      id,
    ).get('optimizations/' + id);

    return AppHttp.execute(observable,
      (data: IOptimization) => {
        // this.sharedService.setHierarchyLink(IAsset.Type.TABLE, data.modelId, IAsset.Type.OPTIMIZATION, data.id);
        // this.sharedService.setHierarchyLink(IAsset.Type.TABLE, data.outputModelId, IAsset.Type.OPTIMIZATION, data.id);
        this._data.view = data;
        this._data.viewset = null;
        this._observables.view.next(data);
      },
    );
  }

  getActiveProcess(item: IOptimization): Observable<IProcess> {
    if (item.status === IOptimization.OptimizationStatus.RUNNING) {
      return this.processes.getByTarget(item.id, IAsset.Type.OPTIMIZATION);
    } else {
      return Observable.of(null);
    }
  }

  create(item: IOptimizationCreate): Observable<IOptimization> {
    const data = jQuery.extend(true, {}, item);

    const observable = this.http.post('optimizations', data);

    return AppHttp.execute(observable,
      (optimization: IOptimization) => {
        this._data.view = optimization;
        this.events.emit(IEvent.Type.CREATE_OPTIMIZATION, optimization);
        this.events.emit(IEvent.Type.UPDATE_OPTIMIZATION_LIST);
        this.notifications.create('Optimization created: ' + optimization.name);
      },
    );
  }

  update(optimizationId: TObjectId, data: IOptimizationUpdate): Observable<IOptimization> {
    const observable = this.http.put('optimizations/' + optimizationId, data);

    return AppHttp.execute(observable,
      (data: IOptimization) => {
        this._data.view = data;
        this._observables.view.next(data);

        // clear all related data
        delete this._data.viewset;

        // RESERVED // this._data.list && this.list() // experimental // refresh the list
        this.events.emit(IEvent.Type.UPDATE_OPTIMIZATION_LIST);
        this.events.emit(IEvent.Type.UPDATE_OPTIMIZATION, { id: optimizationId });
        this.notifications.create('Optimization updated: ' + data.name);
      },
    );
  }

  'delete'(item: IOptimization): Observable<IOptimizationId> {
    const observable = this.http.delete('optimizations/' + item.id);

    return AppHttp.execute(observable,
      (data: IOptimizationId) => {
        if (this._data.view && 'id' in this._data.view && this._data.view.id === data.id) {
          this._data.view = null;
          this._observables.view.next(this._data.view);
        }
        this.events.emit(IEvent.Type.UPDATE_OPTIMIZATION_LIST);
        this.events.emit(IEvent.Type.DELETE_OPTIMIZATION, { id: item.id });
        this.notifications.create('Optimization deleted: ' + item.name);
      },
    );
  }
}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      CREATE_OPTIMIZATION = 'CREATE_OPTIMIZATION',
      UPDATE_OPTIMIZATION_LIST = 'UPDATE_OPTIMIZATION_LIST',
      UPDATE_OPTIMIZATION = 'UPDATE_OPTIMIZATION',
      DELETE_OPTIMIZATION = 'DELETE_OPTIMIZATION',
    }
  }
}
