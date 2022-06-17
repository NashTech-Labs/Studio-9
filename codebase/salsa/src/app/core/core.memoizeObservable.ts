import * as _ from 'lodash';
import 'rxjs/add/operator/publishReplay';
import { Observable } from 'rxjs/Observable';
/**
 * MemoizeObservable method decorator
 * @returns {(target:any, propertyKey:string, descriptor:PropertyDescriptor)=>undefined}
 */
export function MemoizeObservable(resolver?: () => any) {
  return function (target, propertyKey: string) {
    const original: () => Observable<any> = target[propertyKey];
    return target[propertyKey] = _.memoize(function (...args) {
      return original.apply(this, args).publishReplay().refCount();
    }, resolver);
  };
}
