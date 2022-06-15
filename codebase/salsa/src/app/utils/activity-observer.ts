import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { Observable } from 'rxjs/Observable';
import { merge } from 'rxjs/observable/merge';
import { distinctUntilChanged } from 'rxjs/operators/distinctUntilChanged';
import { share } from 'rxjs/operators/share';
import { Subscription } from 'rxjs/Subscription';

export class ActivityObserver {
  private _output: BehaviorSubject<boolean> = new BehaviorSubject<boolean>(false);
  private _input: Observable<any>;
  private _inputSubscription: Subscription;

  // use it with async pipe (to prevent a lot of calls from digest)
  get active(): Observable<boolean> {
    return this._output.pipe(distinctUntilChanged());
  }

  // use it in sync way (when there are no any digest stuff)
  get isActive(): boolean {
    return this._output.getValue();
  }

  observe<T>(action: Observable<T>): Observable<T> {
    this._output.next(true);

    const newInput = action.pipe(share());
    if (this._input) {
      this._input = merge(this._input, newInput);
    } else {
      this._input = newInput;
    }

    if (this._inputSubscription) {
      this._inputSubscription.unsubscribe();
    }
    this._inputSubscription = this._input.subscribe(
      () => {},
      () => this._inputComplete(),
      () => this._inputComplete(),
    );

    return newInput;
  }

  private _inputComplete() {
    if (this._inputSubscription) {
      this._inputSubscription.unsubscribe();
    }
    this._input = undefined;
    this._output.next(false);
  }
}
