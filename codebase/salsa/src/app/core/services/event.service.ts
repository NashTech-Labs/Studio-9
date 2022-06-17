import { EventEmitter, Injectable } from '@angular/core';

import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

export interface IEvent {
  type: IEvent.Type;
  data?: any;
}

export namespace IEvent {
  export const enum Type {}
}

@Injectable()
export class EventService {

  protected _observable: EventEmitter<IEvent> = new EventEmitter<IEvent>();

  get observable(): Observable<IEvent> {
    return this._observable.asObservable();
  }

  constructor() {}

  subscribe(generatorOrNext: (value: IEvent) => void): Subscription {
    return this.observable.subscribe(generatorOrNext);
  }

  emit(type: IEvent.Type, data?: any): void {
    this._observable.emit({
      type: type,
      data: data,
    });
  }

  filter(predicate: (value: IEvent, index: number) => boolean, thisArg?: any): Observable<IEvent> {
    return this._observable.filter(predicate, thisArg);
  }
}
