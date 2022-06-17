import { Injectable } from '@angular/core';

import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';

@Injectable()
export class AppHttpMock {
  active: boolean = false;

  getToken(...args) {
  }

  request(...args) {
    return Observable.of(args[1]);
  }

  get(...args) {
    return Observable.of([...args]);
  }

  post(...args) {
    return Observable.of(args[1]);
  }

  put(...args) {
    return Observable.of(args[1]);
  }

  patch(...args) {
    return Observable.of(args[1]);
  }

  'delete'(...args) {
    return Observable.of(args[1]);
  }

  upload(...args) {
    return Observable.of(args[1]);
  }
}
