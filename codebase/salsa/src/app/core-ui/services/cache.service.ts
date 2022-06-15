import { Injectable } from '@angular/core';

import * as _ from 'lodash';
import { timer } from 'rxjs/observable/timer';

import { StorageService } from '../../core/services/storage.service';

@Injectable()
export class CacheService {
  private _storage: {
    [k: string]: {
      value: any,
      expires: number,
    },
  } = {};

  constructor(
    store: StorageService,
  ) {
    this._storage = store.get('cacheStorage') || {};
    timer(1000, 30000).subscribe(() => {
      const now = Date.now();
      Object.assign(this._storage, store.get('cacheStorage'));
      const newValue = _.pickBy(this._storage, _ => _.expires > now);
      store.set('cacheStorage', newValue);
      this._storage = newValue;
    });
  }

  get<T>(key: string): T {
    const datum = this._storage[key];
    return (datum && datum.expires > Date.now()) ? datum.value : null;
  }

  set<T>(key: string, value: T, expires: number = Date.now() + 3600000): T {
    this._storage[key] = {
      value,
      expires,
    };

    return value;
  }
}
