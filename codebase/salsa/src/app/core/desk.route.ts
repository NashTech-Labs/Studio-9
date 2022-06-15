import { Type } from '@angular/core';
import { Route } from '@angular/router';

import * as _ from 'lodash';

import { FeatureToggleRoute } from './services/feature-toggle.service';

export interface DeskRoute extends FeatureToggleRoute {
  sideComponent: Type<any>;
}

export namespace DeskRoute {
  export function isDeskRoute(route: Route): route is DeskRoute {
    return ('sideComponent' in route);
  }

  export function parse(routes: Route[]): Route[] {
    return FeatureToggleRoute.parse(routes).map(route => {
      const out: Route = _.omit(route, 'sideComponent', 'component');
      if (isDeskRoute(route)) { // this is a DeskRoute; children are simple routes
        const mainSubRoute: Route = {path: '', component: route.component};
        const sideSubRoute: Route = {path: '', component: route.sideComponent, outlet: 'side'};
        mainSubRoute.children = route.children || [];
        out.children = [mainSubRoute, sideSubRoute];
        if (out.canDeactivate) {
          mainSubRoute.canDeactivate = out.canDeactivate;
          delete out.canDeactivate;
        }
      } else if (route.component) { // this is a Route with no sidebar; children are simple routes
        out.component = route.component;
      } else if (route.children) { // this is a Route with no component; children are DeskRoute instances
        out.children = parse(route.children);
      }

      return out;
    });
  }
}
