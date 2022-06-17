import { ReplaySubject } from 'rxjs/ReplaySubject';

import { EventService, IEvent } from './event.service';

/**
 * @deprecated
 */
export abstract class DataService {

  // TODO: naming
  protected _type = null; // should be assigned in child services

  protected _data: any = {
    list: null,
    view: null,
    edit: null,
  };

  protected _observables: any = {
    list: null, // Experimental
    view: null,
    edit: null,
  };

  get data() {
    return this._data;
  }
  get observables() {
    return this._observables;
  }

  constructor(
    protected events: EventService,
  ) {
    // init observers
    ['list', 'view', 'edit'].forEach(prop => {
       // buffer last value
      this._observables[prop] = new ReplaySubject(1);
    });

    // init refresh
    this.events
      .filter(e => e.type === IEvent.Type.PROCESS_COMPLETED)
      .subscribe(this._refresh.bind(this));

    // init reset
    this.events
      .filter(e => e.type === IEvent.Type.SESSION_CLEARED)
      .subscribe(this.reset.bind(this));
  }

  view(item?: Object): void {
    const data = item ? jQuery.extend(true, {}, item) : null;

    this._data.view = data;
    this._observables.view.next(data);
  }

  // experimental
  reset() {
    ['list', 'view', 'edit'].forEach(prop => {
      this._data[prop] = null;
    });
  }

  private _refresh(event: IEvent): void {
    if (!event.data.target || event.data.target !== this._type) { return; }

    // update list
    let list = (this._data.list || []).map(item => item.id);
    list.length && list.indexOf(event.data.id) > -1 && this['list'] && this['list']();

    // update view
    this._data.view && this._data.view.id === event.data.id && this['get'] && this['get'](event.data.id);

    // update edit
    //this._data.edit && this._data.edit.id === event.data.id && this['get'] && this['get'](event.data.id);
  }
}
