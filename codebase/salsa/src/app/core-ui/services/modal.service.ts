import { Injectable } from '@angular/core';

import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';

@Injectable()
export class ModalService {
  confirm(message: string): Observable<boolean> {
    return Observable.of(confirm(message));
  }

  alert(message: string): Observable<boolean> {
    alert(message);
    return Observable.of(true);
  }
}
