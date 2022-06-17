import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve, RouterModule, RouterStateSnapshot, Routes } from '@angular/router';

import { Observable } from 'rxjs/Observable';
import { first } from 'rxjs/operators/first';

import { albumsRoutes } from './albums/albums.routes';
import { AUTH_PROVIDERS, IsAuthenticated } from './app.routes.auth';
import { EmailConfirmationComponent } from './components/email-confirmation.component';
import { LayoutDeskComponent } from './components/layout-desk.component';
import { SigninPasswordConfirmationComponent } from './components/signin-password-confirmation.component';
import { SigninPasswordComponent } from './components/signin-password.component';
import { SigninComponent } from './components/signin.component';
import { SignOutComponent } from './components/signout.component';
import { SignupComponent } from './components/signup.component';
import { composeRoutes } from './compose/compose.routes';
import { IfMocksGuard } from './core/core.mocks-only';
import { DeskRoute } from './core/desk.route';
import { IUser } from './core/interfaces/user.interface';
import { UserService } from './core/services/user.service';
import { deployRoutes } from './deploy/deploy.routes';
import { developRoutes } from './develop/develop.routes';
import { diaaRoutes } from './diaa/diaa.routes';
import { experimentsRoutes } from './experiments/experiments.routes';
import { libraryRoutes } from './library/library.routes';
import { optimizeRoutes } from './optimize/optimize.routes';
import { pipelineRoutes } from './pipelines/pipeline.routes';
import { playRoutes } from './play/play.routes';
import { processesRoutes } from './processes/processes.routes';
import { trainRoutes } from './train/train.routes';
import { usersRoutes } from './users/users.routes';
import { visualizeRoutes } from './visualize/visualize.routes';

@Injectable()
export class LoggedInUserResolver implements Resolve<IUser | null> {
  constructor(private service: UserService) {}
  resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<IUser | null> {
    return this.service.user.pipe(first());
  }
}

export const appRoutes: Routes = [
  {
    path: '',
    redirectTo: 'signin',
    pathMatch: 'full',
  },
  {
    path: 'signin',
    resolve: {
      user: LoggedInUserResolver,
    },
    component: SigninComponent,
  },
  {
    path: 'signin/password',
    component: SigninPasswordComponent,
  },
  {
    path: 'signin/password/complete',
    component: SigninPasswordConfirmationComponent,
  },
  {
    path: 'signup',
    component: SignupComponent,
  },
  {
    path: 'signout',
    component: SignOutComponent,
  },
  {
    path: 'emailconfirmation',
    component: EmailConfirmationComponent,
  },
  {
    path: 'desk',
    component: LayoutDeskComponent,
    canActivate: [IsAuthenticated],
    children: DeskRoute.parse([
      ...libraryRoutes,
      ...albumsRoutes,
      ...composeRoutes,
      ...diaaRoutes,
      ...trainRoutes,
      ...playRoutes,
      ...optimizeRoutes,
      ...visualizeRoutes,
      ...deployRoutes,
      ...developRoutes,
      ...processesRoutes,
      ...usersRoutes,
      ...pipelineRoutes,
      ...experimentsRoutes,
      ...pipelineRoutes,
    ]),
  },
];

export const APP_ROUTER_PROVIDERS = [
  IfMocksGuard,
  LoggedInUserResolver,
  AUTH_PROVIDERS,
];

export const routing = RouterModule.forRoot(appRoutes);
