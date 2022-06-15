import { Injectable } from '@angular/core';

import { Observable } from 'rxjs/Observable';

import {
  IAsset,
  IAssetListRequest,
  IAssetService,
  IBackendList,
  IObjectId,
  TObjectId,
} from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { AppHttp } from '../core/services/http.service';
import { NotificationService } from '../core/services/notification.service';
import { ProcessService } from '../core/services/process.service';
import { MiscUtils } from '../utils/misc';

import { deployConfig } from './deploy.config';
import { IScriptDeployment, IScriptDeploymentCreate, IScriptDeploymentUpdate } from './script-deployment.interface';


@Injectable()
export class ScriptDeploymentService implements IAssetService<IScriptDeployment, IScriptDeploymentCreate> {
  readonly assetType: IAsset.Type = IAsset.Type.SCRIPT_DEPLOYMENT;

  private baseUrl = 'script-deployments';

  constructor(
    private http: AppHttp,
    private events: EventService,
    private notifications: NotificationService,
    private processService: ProcessService,
  ) {}

  create(data: IScriptDeploymentCreate): Observable<IScriptDeployment> {
    // POST '/script-deployments'
    const observable = this.http.post(this.baseUrl, data);

    return AppHttp.execute(observable, (item: IScriptDeployment) => {
      this.events.emit(IEvent.Type.CREATE_SCRIPT_DEPLOYMENT, item);
      this.events.emit(IEvent.Type.UPDATE_SCRIPT_DEPLOYMENT_LIST);
      this.notifications.create('Script Deployment has been created: ' + item.name);
    });
  }

  update(id: TObjectId, data: IScriptDeploymentUpdate): Observable<IScriptDeployment> {
    // PUT '/script-deployments/:id'
    const observable = this.http.put(`${this.baseUrl}/${id}`, data);

    return AppHttp.execute(observable, (item: IScriptDeployment) => {
      this.events.emit(IEvent.Type.UPDATE_SCRIPT_DEPLOYMENT_LIST);
      this.events.emit(IEvent.Type.UPDATE_SCRIPT_DEPLOYMENT, { id: id });
      this.notifications.create('Script Deployment has been updated: ' + item.name);
    });
  }

  list(params?: IAssetListRequest): Observable<IBackendList<IScriptDeployment>> {
    // GET '/script-deployments'
    const observable = this.http.get(this.baseUrl, params);

    return AppHttp.execute(observable);
  }

  'delete'(item: IScriptDeployment): Observable<IObjectId> {
    // DELETE '/script-deployments/:id'
    const observable = this.http.delete(`${this.baseUrl}/${item.id}`);

    return AppHttp.execute(observable,
      () => {
        this.events.emit(IEvent.Type.UPDATE_SCRIPT_DEPLOYMENT_LIST);
        this.events.emit(IEvent.Type.DELETE_SCRIPT_DEPLOYMENT, { id: item.id });
        this.notifications.create('Script Deployment has been deleted: ' + item.name);
      },
    );
  }

  get(id: TObjectId): Observable<IScriptDeployment> {
    // GET '/script-deployments/:id'
    const observable = this.http.get(`${this.baseUrl}/${id}`);

    return AppHttp.execute(observable);
  }

  getActiveProcess(item: IScriptDeployment): Observable<IProcess> {
    if (deployConfig.scriptDeployment.status.hasProcess[item.status]) {
      return this.processService.getByTarget(item.id, IAsset.Type.SCRIPT_DEPLOYMENT);
    } else {
      return Observable.of(null);
    }
  }

  download(id: TObjectId): Observable<boolean> {
    // GET '/script-deployments/:id/download'
    const observable = this.http
      .get(`${this.baseUrl}/${id}/download`)
      .flatMap(url => {
        return MiscUtils.downloadUrl(url, `${id}.zip`);
      })
      .publish();

    observable.connect(); // make sure this runs irrespective of subscribers

    return observable;
  }

}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      CREATE_SCRIPT_DEPLOYMENT = 'CREATE_SCRIPT_DEPLOYMENT',
      UPDATE_SCRIPT_DEPLOYMENT_LIST = 'UPDATE_SCRIPT_DEPLOYMENT_LIST',
      UPDATE_SCRIPT_DEPLOYMENT = 'UPDATE_SCRIPT_DEPLOYMENT',
      DELETE_SCRIPT_DEPLOYMENT = 'DELETE_SCRIPT_DEPLOYMENT',
    }
  }
}
