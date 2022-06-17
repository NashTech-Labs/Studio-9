import { Injectable } from '@angular/core';

import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';

@Injectable()
export class EventServiceMock {
  subscribe() {
  }

  emit(...args) {
    return Observable.of(...args);
  }

  filter() {
    return Observable.of({
      data: {
        target: {},
      },
    });
  }
}
