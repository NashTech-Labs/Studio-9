import { Injectable } from '@angular/core';

import 'rxjs/add/observable/forkJoin';
import 'rxjs/add/observable/of';
import 'rxjs/add/operator/mergeMap';
import 'rxjs/add/operator/share';
import { Observable } from 'rxjs/Observable';

import config from '../config';
import { IAsset, IBackendList, TObjectId } from '../core/interfaces/common.interface';
import { IProject, IProjectCreate, IProjectFolder, IProjectUpdate } from '../core/interfaces/project.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { AppHttp } from '../core/services/http.service';
import { NotificationService } from '../core/services/notification.service';

@Injectable()
export class ProjectService {

  private readonly basePath = 'projects';

  constructor(
    protected http: AppHttp,
    protected events: EventService,
    private notifications: NotificationService,
  ) {}

  list(): Observable<IBackendList<IProject>> {
    const observable = this.http.get(this.basePath);

    return AppHttp.execute(observable);
  }

  create(data: IProjectCreate): Observable<IProject> {
    const observable = this.http.post(this.basePath, data);

    return AppHttp.execute(observable, (project: IProject) => {
      this.events.emit(IEvent.Type.CREATE_PROJECT, project);
      this.events.emit(IEvent.Type.UPDATE_PROJECT_LIST);
      this.notifications.create(`Project created: "${project.name}"`);
    });
  }

  get(id: TObjectId): Observable<IProject> {
    return this.http.get(this.basePath + '/' + id);
  }

  update(id: TObjectId, data: IProjectUpdate) {
    const observable = this.http.put(this.basePath + '/' + id, data);
    return AppHttp.execute(observable, () => {
      this.events.emit(IEvent.Type.UPDATE_PROJECT, { id });
      this.events.emit(IEvent.Type.UPDATE_PROJECT_LIST);
      this.notifications.create(`Project updated: "${data.name}"`);
    });
  }

  'delete'(data: IProject) {
    const observable = this.http.delete(this.basePath + '/' + data.id);
    return AppHttp.execute(observable, () => {
      this.events.emit(IEvent.Type.DELETE_PROJECT, { id: data.id });
      this.events.emit(IEvent.Type.UPDATE_PROJECT_LIST);
      this.notifications.create(`Project deleted: "${data.name}"`);
    });
  }

  createFolder(projectId: TObjectId, path): Observable<IProjectFolder> {
    const observable: Observable<IProjectFolder> = this.http.post(`${this.basePath}/${projectId}/folders`, {
      path: path,
    });
    return AppHttp.execute(observable, folder => {
      this.events.emit(IEvent.Type.UPDATE_PROJECT, { id: projectId });
      this.events.emit(IEvent.Type.UPDATE_PROJECT_LIST);
      this.notifications.create(`Folder created: "${folder.path}"`);
    });
  }

  deleteFolder(projectId: TObjectId, folderId: TObjectId) {
    const observable = this.http.delete(`${this.basePath}/${projectId}/folders/${folderId}`);
    return AppHttp.execute(observable, () => {
      this.events.emit(IEvent.Type.UPDATE_PROJECT, { id: projectId });
      this.events.emit(IEvent.Type.UPDATE_PROJECT_LIST);
      this.notifications.create(`Folder deleted`);
    });
  }

  linkAssets<T extends IAsset>(assetType: IAsset.Type, project: IProject, items: T[], folderId?: TObjectId): Observable<T[]> {
    return this.updateLinkedAssets<T>(assetType, project, items, folderId);
  }

  unlinkAssets<T extends IAsset>(assetType: IAsset.Type, project: IProject, items: T[]): Observable<T[]> {
    return this.updateLinkedAssets<T>(assetType, project, items, null, true);
  }

  private updateLinkedAssets<T extends IAsset>(assetType: string, project: IProject, items: T[], folderId?: TObjectId, unlink?: boolean): Observable<T[]> {
    interface RequestResult {
      success: boolean;
      item: T;
    }

    const actions = items.map((item: T) => {
      const url = `${this.basePath}/${project.id}/${config.asset.aliasesPlural[assetType]}/${item.id}`;

      // we need these 'execute' calls, cause only the end 'execute' for bulkAction produces too many canceled requests
      const requestOptions = {
        noNotifications: true,
        deserialize: (): RequestResult => {
          return { success: true, item: item };
        },
        catchWith: (): Observable<RequestResult> => {
          return Observable.of({ success: false, item: item });
        },
      };
      if (unlink) {
        return AppHttp.execute(this.http.delete(url, null, requestOptions));
      } else {
        return AppHttp.execute(this.http.put(url, {folderId}, null, requestOptions));
      }
    });

    const bulkAction: Observable<T[]> = Observable
      .forkJoin(...actions)
      .flatMap((responses: RequestResult[]) => {
        const doneItems: T[] = [], errorItems: T[] = [];
        responses.forEach((response) => {
          (response.success ? doneItems : errorItems).push(response.item);
        });

        if (doneItems.length) {
          const names = doneItems.map(_ => _.name);
          const labelsPool = doneItems.length > 1
            ? config.asset.labelsPlural
            : config.asset.labels;
          this.notifications.create(`
            ${labelsPool[assetType]} "${names.join('", "')}" successfully
            ${unlink ? 'unlinked from' : 'linked to'} Project "${project.name}"
          `);
          this.events.emit(IEvent.Type.UPDATE_PROJECT_ASSETS, project.id);
        }

        if (errorItems.length) {
          const names = errorItems.map(_ => _.name);
          const labelsPool = errorItems.length > 1
            ? config.asset.labelsPlural
            : config.asset.labels;
          this.notifications.create(
            `${labelsPool[assetType]} "${names.join('", "')}" failed to be
            ${unlink ? 'unlinked from' : 'linked to'} Project "${project.name}"`,
            config.notification.level.values.DANGER);
        }
        return Observable.of(doneItems);
      })
      .share();

    return AppHttp.execute(bulkAction);
  }
}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      CREATE_PROJECT = 'CREATE_PROJECT',
      UPDATE_PROJECT_LIST = 'UPDATE_PROJECT_LIST',
      DELETE_PROJECT = 'DELETE_PROJECT',
      UPDATE_PROJECT = 'UPDATE_PROJECT',
      UPDATE_PROJECT_ASSETS = 'UPDATE_PROJECT_ASSETS',
    }
  }
}
