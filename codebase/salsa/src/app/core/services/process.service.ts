import { Injectable } from '@angular/core';

import 'rxjs/add/observable/empty';
import { Observable } from 'rxjs/Observable';
import { ReplaySubject } from 'rxjs/ReplaySubject';
import { Subscription } from 'rxjs/Subscription';

import config from '../../config';
import { IProcessSearchParams } from '../../processes/process.interfaces';
import { IAsset, IBackendList, TObjectId } from '../interfaces/common.interface';
import { IProcess } from '../interfaces/process.interface';

import { DataService } from './data.service';
import { EventService, IEvent } from './event.service';
import { AppHttp, AppHttpError } from './http.service';
import { NotificationService } from './notification.service';

// fetch and update processes (realtime)
// TODO: experimental service
// TODO: specify typings

// TODO: refactoring - it seems that we don't need to use subscribe() method
// get() and getByTarget() should automatically push item into _subscriptions
// also there is something strange with count property (it seems redundant to ++ and -- it)

interface ProcessSubscription {
  data: IProcess;
  observable: ReplaySubject<IProcess>;
  completeObservable: ReplaySubject<IProcess>;
}

@Injectable()
export class ProcessService extends DataService {
  readonly INTERVAL = 5000; // milliseconds // update (if queue is not empty)

  protected _type = 'PROCESS';

  protected _subscriptions: ProcessSubscription[] = [];

  protected _data = {
    list: null,
    view: null,
    edit: null,
    targets: <{[target: string]: {[id: string]: IProcess}}> {},
  };

  get data() {
    return this._data;
  }

  constructor(protected http: AppHttp,
              protected events: EventService,
              private notifications: NotificationService) {
    super(events);

    // init data
    config.asset.list.map(item => {
      let alias = config.asset.aliasesPlural[item];
      this._data.targets[alias] = this._data.targets[alias] || {};
    });

    // init listeners (realtime updates)
    window.setInterval(() => {
      this.__refresh();
    }, config.process.interval || this.INTERVAL);

    // RESERVED
    // init lazy listeners (realtime updates)
  }

  find(id): IProcess {
    return (this._data.list || []).filter(item => item.id === id).pop();
  }

  // Experimental // TODO: do we need this._data.view?
  get(id: TObjectId): Observable<IProcess> {
    // GET '/processes/:id'
    const observable = this.http.get('processes/' + id, null, {
      deserialize: (res) => this._deserialize(res),
    });

    return AppHttp.execute(observable,
      (data: IProcess) => {
        this._data.view = data;
        this._observables.view.next(data);
      },
    );
  }

  list(params?: IProcessSearchParams): Observable<IBackendList<IProcess>> {
    return this.http.get('processes', params);
  }

  getByTarget(targetId: TObjectId, targetType: IAsset.Type): Observable<IProcess> {
    const url = this._getTargetProcessURL(targetId, targetType);

    const observable = this.http.get(url, null, {
      deserialize: (res) => this._deserialize(res),
      catchWith: (err: AppHttpError) => {
        if (err.httpStatus === 401 || err.httpStatus === 404) { // TODO: 404 only
          console.warn(`Process for ${config.asset.labels[targetType]} ${targetId} not found`);
          return Observable.empty();
        }

        return Observable.throw(err);
      },
    });

    return AppHttp.execute(observable,
      (data: IProcess) => {
        this._data.view = data;
        this._observables.view.next(data);
      },
    );
  }

  cancel(process: IProcess) {
    const observable = this.http.post(`processes/${process.id}/cancel`, null);
    return AppHttp.execute(observable, () => {
      const target = process.target.toUpperCase();
      // TODO: move to subscriptions from interested services
      this.events.emit(<IEvent.Type> `UPDATE_${target}`, { id: process.targetId });
      this.events.emit(<IEvent.Type> `UPDATE_${target}_LIST`);
      this.notifications.create(`${config.asset.labels[target]} process cancelled`);
    });
  }

  /**
   * Updates (mutates) process on any process change fetched from backend and emits on process complete.
   * @param process
   */
  observe(process: IProcess): Observable<IProcess> {
    const subscription = this._getSubscription(process, true);

    return new Observable<IProcess>(subscriber => {
      const stateSubscription = subscription.observable.subscribe(updated => {
        Object.assign(process, updated);
      });

      const completionSubscription = subscription.completeObservable.subscribe(subscriber);

      return () => {
        stateSubscription.unsubscribe();
        completionSubscription.unsubscribe();
      };
    }).share();
  }

  subscribeByTarget(targetId: TObjectId, targetType: IAsset.Type, onNext: Function): Subscription {
    return this.getByTarget(targetId, targetType)
      .flatMap(process => this.observe(process))
      .subscribe((data) => { onNext(data); });
  }

  // TODO: needs refactoring for better look.
  private __refresh() {
    // TEMP TEMP TEMP
    // should fetch all updated processes not one-by-one
    this._subscriptions.forEach(item => {
      if (item.completeObservable.observers.length === 0) {
        // no listeners - no need to refresh
        this._dropSubscription(item.data);
      } else {
        // poll the server to get process status
        this._get(item.data.id).subscribe((data: IProcess) => {
          item.data = Object.assign(item.data, data);

          item.observable.next(item.data);

          if (item.data.status === IProcess.Status.FAILED) {
            // failed notification
            this.notifications.create(
              `${config.asset.labels[item.data.target]} #${item.data.targetId} ` +
                `is ${config.process.status.labels[item.data.status]}: ${item.data.cause}`,
              config.notification.level.values.DANGER,
            );
            this.events.emit(IEvent.Type.PROCESS_FAILED, item.data);
            // process can be already failed when got into subscription. need to fire this on polling end
            item.completeObservable.next(data);
            this._dropSubscription(item.data);
          } else if (item.data.status === IProcess.Status.COMPLETED) {
            this.events.emit(IEvent.Type.PROCESS_COMPLETED, item.data);
            // process can be already completed when got into subscription. need to fire this on polling end
            item.completeObservable.next(data);
            this._dropSubscription(item.data);
          } else if (item.data.status === IProcess.Status.CANCELLED) {
            this.events.emit(IEvent.Type.PROCESS_CANCELLED, item.data);
            // process can be already cancelled when got into subscription. need to fire this on polling end
            item.completeObservable.next(data);
            this._dropSubscription(item.data);
          }
        });
      }
    });
  }

  // experimental
  private _get(id: TObjectId): Observable<IProcess> {
    // GET '/processes/:id'
    const observable = this.http.get('processes/' + id, null, { deserialize: (res) => this._deserialize(res) });

    return AppHttp.execute(observable,
      (data: IProcess) => {
        this._observables.view.next(data);
      },
    );
  }

  private _deserialize(res: IProcess): IProcess {
    res = Object.assign({}, res);
    if ('target' in res) {
      const target = res.target.toUpperCase();
      switch (target) {
        case 'TRAINING':
          res.target = IAsset.Type.MODEL;
          break;
        case 'DATAUPLOAD':
          res.target = IAsset.Type.TABLE;
          break;
        case 'TABLESTATS':
          res.target = IAsset.Type.TABLE_STATS;
          break;
        default:
          res.target = <IAsset.Type> target;
      }
      if (!res.id) {
        res.id = res.targetId;
      }
    }
    return res;
  }

  private _getTargetProcessURL(targetId: TObjectId, targetType: IAsset.Type): string {
    //noinspection JSRedundantSwitchStatement
    switch (targetType) {
      case IAsset.Type.TABLE_STATS:
        return `tables/${targetId}/stats/process`;
      default:
        // '/flows|tables|models|predictions|.../:id/process'
        const targetAlias = config.asset.aliasesPlural[targetType] || 'flows';
        return `${targetAlias}/${targetId}/process`;
    }
  }

  private _getSubscription(process: IProcess, create?: boolean): ProcessSubscription {
    let subscription = (this._subscriptions || []).filter(item => item.data.id === process.id).pop();
    if (!subscription && create) {
      subscription = {
        data: {...process},
        observable: new ReplaySubject(1), // buffer last value
        completeObservable: new ReplaySubject(1), // buffer last value
      };

      this._subscriptions.push(subscription);

      if (process.target) {
        let targetAlias = config.asset.aliasesPlural[process.target];
        this._data.targets[targetAlias][process.targetId] = subscription.data;
      }
    }

    return subscription;
  }

  private _dropSubscription(process: IProcess): void {
    let subscription = this._getSubscription(process);

    if (subscription) {
      subscription.observable.complete();
      subscription.completeObservable.complete();
      let index = this._subscriptions.indexOf(subscription);
      index > -1 && this._subscriptions.splice(index, 1);
    }

    // remove symlink
    // RESERVED delete this._data.targets[config.assets.type.aliasesPlural[process.target]];
  }
}

declare module './event.service' {
  export namespace IEvent {
    export const enum Type {
      PROCESS_FAILED = 'PROCESS_FAILED',
      PROCESS_COMPLETED = 'PROCESS_COMPLETED',
      PROCESS_CANCELLED = 'PROCESS_CANCELLED',
    }
  }
}
