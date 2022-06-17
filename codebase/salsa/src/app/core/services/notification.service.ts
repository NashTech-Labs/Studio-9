import { Injectable } from '@angular/core';

import { Observable } from 'rxjs/Observable';
import { ReplaySubject } from 'rxjs/ReplaySubject';

import config from '../../config';
import { Omit } from '../../utils/Omit';
import { TObjectId } from '../interfaces/common.interface';
import { INotification, INotificationData, INotificationOptions } from '../interfaces/notification.interface';

import { AppHttpError } from './http.service';

export interface IUploadNotification extends INotification {
  readonly onError: (error: AppHttpError) => void;
  readonly onProgress: (progress: number) => void;
  readonly onComplete: () => void;
}

@Injectable()
export class NotificationService {
  private INTERVAL = 3000;
  private _data: INotification[] = [];

  get data() {
    return this._data;
  }

  constructor() {
    // to refresh notifications
    window.setInterval(() => {
      this._refresh();
    }, config.notification.interval || this.INTERVAL);
  }

  find(id: TObjectId): INotification {
    return (this._data || []).filter(item => item.id === id).pop();
  }

  create(item: Partial<INotification> | string, level?: string, options?: INotificationOptions): INotification {
    let notification: INotification;

    const defaults: INotification = {
      id: <TObjectId> '_' + Math.random().toString(36).substr(2, 9),
      type: config.notification.type.values.DEFAULT,
      data: {},
      created: Date.now(),
    };

    // create notification object
    if (typeof item === 'string') {
      notification = jQuery.extend(true, {}, defaults, {
        data: {
          level: level || config.notification.level.values.INFO,
          text: item, // text
        },
      });

    } else {
      notification = jQuery.extend(true, {}, defaults, item);
    }

    notification.options = <INotificationOptions> Object.assign({}, config.notification.defaults, options);

    if (notification.data.level === config.notification.level.values.DANGER) {
      notification.options.timeout = 0;
    }

    // set "observable" data props
    if (notification.dataAsync) {
      let observable = <Observable<any>> notification.dataAsync;

      notification.data = {};
      observable.subscribe(this._updateHandler(notification));
    }

    this._data.push(notification);

    return notification;
  }

  createUploadNotification(files: File | File[], onCancel?: () => void): IUploadNotification {
    const notificationDataObservable = new ReplaySubject<INotificationData>(1);
    const notificationData: INotificationData = {
      text: 'Initiating',
      progress: 0,
      file: Array.isArray(files) ? null : files,
      files: Array.isArray(files) ? files : null,
      canCancel: true,
      cancel: onCancel,
    };

    const notification = this.create(
      {type: config.notification.type.values.FILE_UPLOAD, dataAsync: notificationDataObservable},
      config.notification.level.values.INFO,
      {timeout: 0},
    );

    notificationDataObservable.next(Object.assign({}, notificationData));

    return {
      ...notification,
      onError: error => {
        this.update(notification.id, {options: {...notification.options, timeout: 0}});

        notificationDataObservable.next({
          ...notificationData,
          level: config.notification.level.values.DANGER,
          text: error.message,
          canCancel: false,
        });
      },
      onProgress: progress => {
        notificationDataObservable.next({
          ...notificationData,
          text: 'Uploading',
          progress: progress,
          canCancel: true,
        });
      },
      onComplete: () => {
        const timeout = Date.now() - notification.created + config.notification.defaults.timeout;
        this.update(notification.id, {options: {...notification.options, timeout}});

        notificationDataObservable.next(Object.assign(notificationData, {
          level: config.notification.level.values.SUCCESS,
          text: 'Uploaded',
          progress: 100,
          canCancel: false,
        }));
      },
    };
  }

  update(id: TObjectId, update: Omit<Partial<INotification>, 'id'>): INotification {
    const notification = this.find(id);

    Object.assign(notification, update);

    // pass // @todo: update notification
    return notification;
  }

  delete(id: TObjectId): TObjectId {
    let index = this.data.reduce((result, item, index) => {
      return item.id === id ? index : result;
    }, -1);

    index > -1 && this._data.splice(index, 1);

    return id;
  }

  private _updateHandler(notification: INotification) {
    return (data: any) => {
      notification.data = data; // unpack observerable data
    };
  }

  private _refresh() {
    let now = Date.now();

    for (let i = this._data.length - 1; i >= 0; i--) {
      let item = this._data[i];
      let dismiss = (
        item.options.timeout
          ? (item.created + item.options.timeout) < now : false
        // add more conditions here
      );

      if (dismiss) {
        if (item.options.onDismiss) {
          item.options.onDismiss(item);
        }

        this._data.splice(i, 1);
      }
    }
  }
}
