import { HttpResponse } from '@angular/common/http';
import { Injectable, Injector } from '@angular/core';

import 'rxjs/add/operator/concatMap';
import 'rxjs/add/operator/takeWhile';
import { Observable } from 'rxjs/Observable';

import config from '../config';
import { IAsset, IObjectId, TObjectId } from '../core/interfaces/common.interface';
import { AssetService } from '../core/services/asset.service';
import { IEvent } from '../core/services/event.service';
import { AppHttp, IServerSentEvent } from '../core/services/http.service';

import {
  IS9Project,
  IS9ProjectCreate,
  IS9ProjectFile,
  IS9ProjectSession,
  IS9ProjectSessionCreate,
  IS9ProjectUpdate,
} from './s9-project.interfaces';
import { IPackageCreate } from './package.interfaces';

@Injectable()
export class S9ProjectService
  extends AssetService<IAsset.Type.S9_PROJECT, IS9Project, IS9ProjectCreate, IS9ProjectUpdate> {

  protected readonly _createEventType: IEvent.Type = IEvent.Type.CREATE_S9_PROJECT;
  protected readonly _updateEventType: IEvent.Type = IEvent.Type.UPDATE_S9_PROJECT;
  protected readonly _deleteEventType: IEvent.Type = IEvent.Type.DELETE_S9_PROJECT;
  protected readonly _listUpdateEventType: IEvent.Type = IEvent.Type.UPDATE_S9_PROJECT_LIST;

  constructor(injector: Injector) {
    super(injector, IAsset.Type.S9_PROJECT);
  }

  build(item: IS9Project, data: IPackageCreate) {
    const observable = this._http.post(this._baseUrl + '/' + item.id + '/build', data);
    return AppHttp.execute(observable,
      () => {
        this._events.emit(IEvent.Type.UPDATE_S9_PROJECT_LIST);
        this._events.emit(IEvent.Type.UPDATE_S9_PROJECT, {id: item.id});
        this._events.emit(IEvent.Type.BUILD_S9_PROJECT, {id: item.id});
        this._notifications.create('S9 Project build process has started' + item.name);
      });
  }

  listFiles(projectId: TObjectId, path?: string): Observable<IS9ProjectFile[]> {
    return this._http.get(`${this._baseUrl}/${projectId}/ls`, {
      path,
      recursive: true,
    });
  }

  getFileContent(projectId: TObjectId, filePath: string): Observable<IS9ProjectFile.Content> {
    const encodedFilePath = this._uriEncodePath(filePath);
    return this._http.get(`${this._baseUrl}/${projectId}/files/${encodedFilePath}`, {}, {
      responseType: 'text',
      getResponse: true,
    }).map((response: HttpResponse<string>) => {
      const lastModifiedFromResponse = response.headers.get('Last-Modified');

      const lastModified = lastModifiedFromResponse
        ? new Date(lastModifiedFromResponse)
        : new Date(0);

      return {
        content: response.body,
        contentType: response.headers.get('Content-Type') || 'text/plain',
        lastModified: lastModified.toISOString(),
      };
    });
  }

  getFileBlob(projectId: TObjectId, filePath: string): Observable<Blob> {
    const encodedFilePath = this._uriEncodePath(filePath);
    return this._http.get(`${this._baseUrl}/${projectId}/files/${encodedFilePath}`, {}, {
      responseType: 'blob',
      getResponse: true,
    }).map((response: HttpResponse<Blob>) => response.body);
  }

  updateFileContent(
    projectId: TObjectId,
    filePath: string,
    data: string,
    lastKnownModifiedTime: string = null,
  ): Observable<IS9ProjectFile> {
    const encodedFilePath = this._uriEncodePath(filePath);
    const observable = this._http.put(`${this._baseUrl}/${projectId}/files/${encodedFilePath}`, data, {}, {
      headers: {
        'If-Unmodified-Since': lastKnownModifiedTime
          ? new Date(lastKnownModifiedTime).toUTCString()
          : new Date().toUTCString(),
      },
    });

    return AppHttp.execute(observable,
      () => {
        this._notifications.create('S9 Project file has been updated: ' + filePath);
      },
    );
  }

  createFileContent(
    projectId: TObjectId,
    filePath: string,
    data: string,
  ): Observable<IS9ProjectFile> {
    const encodedFilePath = this._uriEncodePath(filePath);
    const observable = this._http.put(`${this._baseUrl}/${projectId}/files/${encodedFilePath}`, data, {});
    return AppHttp.execute(observable,
      () => {
        this._notifications.create('S9 Project file has been created: ' + filePath);
      },
    );
  }

  createDirectory(
    projectId: TObjectId,
    directoryPath: string,
  ): Observable<IS9ProjectFile> {
    const encodedDirectoryPath = this._uriEncodePath(directoryPath);
    const observable = this._http.put(`${this._baseUrl}/${projectId}/files/${encodedDirectoryPath}`, null, {}, {
      headers: {
        'Content-Type': 'application/x-directory',
      },
    });

    return AppHttp.execute(observable,
      () => {
        this._notifications.create('S9 Project folder has been created: ' + directoryPath);
      },
    );
  }

  deleteFile(
    projectId: TObjectId,
    filePath: string,
    lastKnownModifiedTime: string,
  ): Observable<IObjectId> {
    const encodedFilePath = this._uriEncodePath(filePath);
    const observable = this._http.delete(`${this._baseUrl}/${projectId}/files/${encodedFilePath}`, {}, {
      headers: {
        'If-Unmodified-Since': new Date(lastKnownModifiedTime).toUTCString(),
      },
    });

    return AppHttp.execute(observable,
      () => {
        this._notifications.create('S9 Project file has been deleted: ' + filePath);
      },
    );
  }

  moveFile(
    projectId: TObjectId,
    file: IS9ProjectFile,
    newFilePath: string,
  ): Observable<IS9ProjectFile> {
    const encodedNewFilePath = this._uriEncodePath(newFilePath);
    const observable = this._http.put(`${this._baseUrl}/${projectId}/files/${encodedNewFilePath}`, '', {}, {
        headers: Object.assign(
          {
            'X-Move-Source': file.name,
          },
          file.type === IS9ProjectFile.Type.DIR ? { 'Content-Type': 'application/x-directory' } : {},
        ),
      },
    );

    return AppHttp.execute(observable,
      () => {
        this._notifications.create('S9 Project file has been moved/renamed: ' + file.name);
      },
    );
  }

  /*
  USE once UC is in place
  copyFile(
    projectId: TObjectId,
    filePath: string,
    newFilePath: string,
  ): Observable<IS9ProjectFile> {
    const encodedNewFilePath = this._uriEncodePath(newFilePath);
    const observable = this._http.put(`${this._baseUrl}/${projectId}/files/${encodedNewFilePath}`, '', {}, {
      headers: {
        'X-Copy-Source': filePath,
      },
    });

    return AppHttp.execute(observable,
      () => {
        this._notifications.create('S9 Project file has been copied: ' + filePath);
      },
    );
  }*/

  isBuildingAvailable(items: IS9Project[]): boolean {
    return items.length === 1 && items[0].status === IS9Project.Status.IDLE;
  }

  isPublishingAvailable(items: IS9Project[]): boolean {
    return items.length === 1 && !!items[0].packageName;
  }

  startSession(s9Project: IS9Project, data: IS9ProjectSessionCreate): Observable<IS9ProjectSession> {
    const observable = this._http.post(`${this._baseUrl}/${s9Project.id}/session`, data);

    return AppHttp.execute(
      observable,
      () => {
        this._events.emit(IEvent.Type.UPDATE_S9_PROJECT_LIST);
        this._events.emit(IEvent.Type.UPDATE_S9_PROJECT, { id: s9Project.id });
        this._events.emit(IEvent.Type.START_S9_PROJECT_SESSION, { projectId: s9Project.id });
        this._notifications.create('S9 Project interactive session has been started');
      },
    );
  }

  stopSession(s9Project: IS9Project): Observable<TObjectId> {
    const observable = this._http.delete(`${this._baseUrl}/${s9Project.id}/session`);

    return AppHttp.execute(
      observable,
      () => {
        this._events.emit(IEvent.Type.UPDATE_S9_PROJECT_LIST);
        this._events.emit(IEvent.Type.UPDATE_S9_PROJECT, { id: s9Project.id });
        this._events.emit(IEvent.Type.STOP_S9_PROJECT_SESSION, { projectId: s9Project.id });
        this._notifications.create('S9 Project interactive session has been stopped');
      },
    );
  }

  getSession(s9ProjectId: TObjectId) {
    return this._http.get(`${this._baseUrl}/${s9ProjectId}/session`, null);
  }

  getSessionStatusStream(s9ProjectId: TObjectId): Observable<IS9ProjectSession.Status> {
    const stopStatusTrackingWhen = [
      IS9ProjectSession.Status.COMPLETED,
      IS9ProjectSession.Status.FAILED,
    ];

    //const statusObservable = this._http.sseStream(
    //  `http://localhost:8080/events`,
    //  {
    //    parse: _ => <IS9ProjectSession.Status>_,
    //    retry: true,
    //  },
    //)
    const statusObservable = this._http
      .sseStream<IS9ProjectSession.Status>(
        `${this._baseUrl}/${s9ProjectId}/session/status`,
        {
          parse: _ => <IS9ProjectSession.Status> _,
          retry: true,
        },
      )
      .filter(_ => _.type === IServerSentEvent.Type.MESSAGE)
      .map(_ => _.data)
      .do(status => {
        if (status === IS9ProjectSession.Status.FAILED) {
          this._notifications.create(
            'Failed to run interactive session. Please try again later.',
            config.notification.level.values.DANGER,
          );
        }
      });

    return statusObservable
      .concatMap(status => {
        if (stopStatusTrackingWhen.includes(status)) {
          this._events.emit(IEvent.Type.UPDATE_S9_PROJECT_LIST);
          this._events.emit(IEvent.Type.UPDATE_S9_PROJECT, { id: s9ProjectId });
          this._events.emit(IEvent.Type.STOP_S9_PROJECT_SESSION, { projectId: s9ProjectId });
          // add second null observable to pass the last status and then complete
          // FIXME: in rxjs:>=6.4.0 use an inclusive option for takeWhile
          return Observable.of(status, null);
        }
        return Observable.of(status);
      })
      .takeWhile(Boolean)
      .share();
  }

  protected _hasProcess(item: IS9Project): boolean {
    return config.s9Project.status.hasProcess[item.status];
  }

  private _uriEncodePath(path: string): string {
    return path
      .split('/')
      .map(segment => encodeURIComponent(segment))
      .join('/');
  }
}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      CREATE_S9_PROJECT = 'CREATE_S9_PROJECT',
      UPDATE_S9_PROJECT_LIST = 'UPDATE_S9_PROJECT_LIST',
      UPDATE_S9_PROJECT = 'UPDATE_S9_PROJECT',
      DELETE_S9_PROJECT = 'DELETE_S9_PROJECT',
      BUILD_S9_PROJECT = 'BUILD_S9_PROJECT',
      START_S9_PROJECT_SESSION = 'START_S9_PROJECT_SESSION',
      STOP_S9_PROJECT_SESSION = 'STOP_S9_PROJECT_SESSION',
    }
  }
}
