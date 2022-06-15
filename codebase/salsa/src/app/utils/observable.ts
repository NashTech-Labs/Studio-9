import { Observable } from 'rxjs/Observable';
import { forkJoin } from 'rxjs/observable/forkJoin';
import { defaultIfEmpty } from 'rxjs/operators/defaultIfEmpty';

/**
 * This is mostly a forkJoin but emits even if some action completes empty
 * @param observables
 * @param fillUpValue
 */
export function bulkAction<T>(observables: Observable<T>[], fillUpValue: T = null): Observable<(T | null)[]> {
  return forkJoin(observables.map(_ => _.pipe(defaultIfEmpty(fillUpValue))));
}
