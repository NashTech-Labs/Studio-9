import { Component } from '@angular/core';

import { Observable } from 'rxjs/Observable';
import { delay } from 'rxjs/operators/delay';
import { switchMap } from 'rxjs/operators/switchMap';

import { AppHttp } from '../core/services/http.service';

@Component({
  selector: 'app-block',
  template: `
    <div *ngIf="_blocked | async" class="app-block-backdrop">
      <app-spinner [visibility]="true" [height]="150"></app-spinner>
    </div>
  `,
})
export class AppBlockComponent {
  _blocked: Observable<boolean>;

  constructor(http: AppHttp) {
    this._blocked = http.active.pipe(switchMap(active => {
      return Observable.of(active).pipe(delay(active ? 500 : 0));
    }));
  }
}
