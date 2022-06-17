import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';

import { Observable } from 'rxjs/Observable';

import { SigninComponent } from './components/signin.component';
import config from './config';
import { UserService } from './core/services/user.service';

@Injectable()
export class IsAuthenticated implements CanActivate {

  constructor(
    private router: Router,
    private userService: UserService,
  ) {}

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> | boolean {
    const isAuthorized = this.userService.isAuthenticated();

    const result: Observable<boolean> = (isAuthorized !== null)
      ? Observable.of(isAuthorized)
      : this.userService.user.first().map(
        user => {
          return !!user;
        },
        () => {
          return false;
        },
      );

    return result.do(canActivate => {
      if (!canActivate) {
        this.router.navigate([config.routes.signin], {
          queryParams: SigninComponent.prepareReturnTo(state),
          replaceUrl: true,
        });
      }
    });
  }
}

export const AUTH_PROVIDERS = [IsAuthenticated, UserService];
