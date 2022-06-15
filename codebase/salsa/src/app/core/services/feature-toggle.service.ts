import { Injectable, Optional } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Route, RouterStateSnapshot } from '@angular/router';

import * as _ from 'lodash';

import { Feature, FeaturesConfig } from '../interfaces/feature-toggle.interface';

@Injectable()
export class FeatureToggleService {
  constructor(
    @Optional() private config: FeaturesConfig,
  ) {
    if (!this.config) {
      this.config = new FeaturesConfig('blacklist', []);
    }
  }

  isFeatureEnabled(feature: Feature) {
    switch (this.config.mode) {
      case 'whitelist':
        return this.config.features.includes(feature);
      case 'blacklist':
        return !this.config.features.includes(feature);
      default:
        throw new Error('I suppose developer guys did something wrong with feature configuration');
    }
  }

  areFeaturesEnabled(features: Feature[]) {
    return _.every(features, _ => this.isFeatureEnabled(_));
  }
}

@Injectable()
export class FeatureToggleGuard implements CanActivate {
  constructor(
    private service: FeatureToggleService,
  ) {}

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    const features = route.data['features'] as Feature[];

    if (!features) {
      throw new Error('FeatureToggleGuard used without features defined in route');
    }

    return _.every(features, _ => this.service.isFeatureEnabled(_));
  }
}

export interface FeatureToggleRoute extends Route {
  children?: FeatureToggleRoute[];
  features?: Feature[];
}

export namespace FeatureToggleRoute {
  export function parse(routes: FeatureToggleRoute[]): Route[] {
    return routes.map(route => {
      if (route.features) {
        route.canActivate = [FeatureToggleGuard, ...(route.canActivate || [])];
        route.data = Object.assign({}, route.data || {}, {
          'features': route.features,
        });
        delete route.features;
      }

      if (route.children) {
        route.children = parse(route.children);
      }

      return route;
    });
  }
}
