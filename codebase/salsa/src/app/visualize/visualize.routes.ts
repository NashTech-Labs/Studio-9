import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';

import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';
import { ReplaySubject } from 'rxjs/ReplaySubject';

import { SpinnerComponent } from '../core-ui/components/spinner.component';
import { IfMocksGuard } from '../core/core.mocks-only';
import { DeskRoute } from '../core/desk.route';
import { IAsset } from '../core/interfaces/common.interface';

import { DashboardEditContextComponent } from './dashboard-edit-context.component';
import { DashboardEditState } from './dashboard-edit-state';
import { DashboardEditComponent } from './dashboard-edit.component';
import { VisualizeContextComponent } from './visualize-context.component';
import { VisualizeGeospatialContextComponent } from './visualize-geospatial-context.component';
import { VisualizeGeospatialComponent } from './visualize-geospatial.component';
import { VisualizeViewComponent } from './visualize-view.component';

@Injectable()
export class DashboardFormResolve implements Resolve<ReplaySubject<DashboardEditState>> {
  resolve(route: ActivatedRouteSnapshot) {
    return Observable.of(new ReplaySubject<DashboardEditState>(1));
  }
}

export const visualizeRoutes: DeskRoute[] = [
  {
    path: 'visualize',
    component: SpinnerComponent,
    sideComponent: VisualizeContextComponent,
    //redirectTo: 'visualize/dashboards/create',
    canActivate: [IfMocksGuard],
  },
  {
    path: 'visualize/geospatial',
    component: VisualizeGeospatialComponent,
    sideComponent: VisualizeGeospatialContextComponent,
    canActivate: [IfMocksGuard],
  },
  {
    path: 'visualize/dashboards',
    component: SpinnerComponent,
    sideComponent: VisualizeContextComponent,
    //redirectTo: 'visualize/dashboards/create',
    canActivate: [IfMocksGuard],
  },
  {
    path: 'visualize/dashboards/create',
    component: DashboardEditComponent,
    sideComponent: DashboardEditContextComponent,
    canActivate: [IfMocksGuard],
    resolve: { subject: DashboardFormResolve },
  },
  {
    path: 'visualize/dashboards/:dashboardId',
    component: VisualizeViewComponent,
    sideComponent: VisualizeContextComponent,
    canActivate: [IfMocksGuard],
  },
  {
    path: 'visualize/dashboards/:dashboardId/edit',
    component: DashboardEditComponent,
    sideComponent: DashboardEditContextComponent,
    canActivate: [IfMocksGuard],
    resolve: { subject: DashboardFormResolve },
  },
];

export const visualizeModuleAssetURLMap = {
  [IAsset.Type.DASHBOARD]: ['/desk', 'visualize', 'dashboards'],
};
