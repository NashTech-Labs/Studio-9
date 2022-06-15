import { Injectable } from '@angular/core';

import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';

@Injectable()
export class ProcessServiceMock {
  subscribe() {
  }

  getByTarget(...args) {
    return Observable.of(...args);
  }
}
