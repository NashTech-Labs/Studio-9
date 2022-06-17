import { Directive, Injectable, Input, Pipe, PipeTransform, TemplateRef, ViewContainerRef } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, RouterStateSnapshot } from '@angular/router';

import { environment } from '../../environments/environment';

export const mocksMode = !!environment.mocks;

/**
 * Add the template content to the DOM unless the condition is true.
 */
@Directive({ selector: '[mocksOnly]'})
export class MocksOnlyDirective {
  private hasView = false;

  constructor(
    private templateRef: TemplateRef<any>,
    private viewContainer: ViewContainerRef,
  ) {}

  @Input() set mocksOnly(condition: boolean) {
    const showContents = !condition || mocksMode;
    if (showContents && !this.hasView) {
      this.viewContainer.createEmbeddedView(this.templateRef);
      this.hasView = true;
    } else if (!showContents && this.hasView) {
      this.viewContainer.clear();
      this.hasView = false;
    }
  }
}

/**
 * mocksOnly method decorator
 * @param defaultReturnValue
 * @returns {(target:any, propertyKey:string, descriptor:PropertyDescriptor)=>undefined}
 */
export function mocksOnly(defaultReturnValue: any) {
  return function (target: any, propertyKey: string, descriptor: PropertyDescriptor) {
    if (!mocksMode) {
      descriptor.value = function(...args: any[]) {
        return typeof defaultReturnValue === 'function'
          ? defaultReturnValue(...args)
          : defaultReturnValue;
      };
    }
  };
}

type valueFunction<T> = () => T;

export function ifMocks<T>(value: T | valueFunction<T>, noMocksValue?: T): T {
  if (mocksMode) {
    return typeof value === 'function'
      ? (<valueFunction<T>> value)()
      : value;
  }
  return noMocksValue;
}

@Injectable()
export class IfMocksGuard implements CanActivate {
  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    return mocksMode;
  }
}

@Pipe({name: 'ifMocks'})
export class IfMocksPipe implements PipeTransform {
  transform<T>(value: T | valueFunction<T>, noMocksValue?: T): T {
    return ifMocks<T>(value, noMocksValue);
  }
}
