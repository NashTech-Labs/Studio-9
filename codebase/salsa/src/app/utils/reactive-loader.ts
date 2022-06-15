import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { Observable } from 'rxjs/Observable';
import { distinctUntilChanged } from 'rxjs/operators/distinctUntilChanged';
import { switchMap } from 'rxjs/operators/switchMap';
import { take } from 'rxjs/operators/take';
import { Subject } from 'rxjs/Subject';
import { Subscription } from 'rxjs/Subscription';

export class ReactiveLoader<T, T1> {
  private _loaded: boolean = false;
  private readonly _requestQueue: Subject<T1> = new Subject<T1>();
  private readonly _resultQueue: Observable<T>;
  private readonly _active: Subject<boolean> = new BehaviorSubject(false);

  constructor(loaderFunction: (T1) => Observable<T>, initiallyLoaded: boolean = false) {
    this._loaded = initiallyLoaded;
    this._resultQueue = this._requestQueue.pipe(switchMap(_ => {
      this._active.next(true);
      this._loaded = false;
      return loaderFunction(_).pipe(take(1)).catch(() => Observable.empty<T>()).do({
        complete: () => {
          this._active.next(false);
        },
      });
    })).map(_ => {
      this._loaded = true;
      return _;
    });
  }

  public load(arg?: T1): void {
    this._requestQueue.next(arg);
  }

  public subscribe(next: (value: T) => void): Subscription {
    return this._resultQueue.subscribe(next);
  }

  public complete() {
    this._requestQueue.complete();
  }

  get active(): Observable<boolean> {
    return this._active.pipe(distinctUntilChanged());
  }

  get loaded(): boolean {
    return this._loaded;
  }

  get value(): Observable<T> {
    return this._resultQueue;
  }
}
