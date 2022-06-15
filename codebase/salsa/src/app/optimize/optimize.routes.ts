import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';

import { Observable } from 'rxjs/Observable';

import { IfMocksGuard } from '../core/core.mocks-only';
import { DeskRoute } from '../core/desk.route';
import { IAsset } from '../core/interfaces/common.interface';

import { OptimizationContextComponent } from './optimization-context.component';
import { OptimizationCreateComponent } from './optimization-create.component';
import { OptimizationViewComponent } from './optimization-view.component';
import { IOptimization } from './optimization.interface';
import { OptimizationService } from './optimization.service';

@Injectable()
export class OptimizationResolve implements Resolve<IOptimization> {

  constructor(private optimizations: OptimizationService) {
  }

  resolve(route: ActivatedRouteSnapshot): Observable<IOptimization> {
    let optimizationId = route.params['optimizationId'];
    return this.optimizations.get(optimizationId);
  }
}

export const optimizeRoutes: DeskRoute[] = [
  {
    path: 'optimization',
    sideComponent: OptimizationContextComponent,
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'create',
      },
      {
        path: 'create',
        component: OptimizationCreateComponent,
      },
      {
        path: ':optimizationId',
        component: OptimizationViewComponent,
        resolve: { optimization: OptimizationResolve },
        canActivate: [IfMocksGuard],
      },
    ],
  },
];

export const optimizeModuleAssetURLMap = {
  [IAsset.Type.OPTIMIZATION]: ['/desk', 'optimization'],
};
