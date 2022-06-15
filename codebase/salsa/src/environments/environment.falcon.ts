import { Provider } from '@angular/core';

import { Feature, FeaturesConfig } from '../app/core/interfaces/feature-toggle.interface';

const featuresConfig: FeaturesConfig = new FeaturesConfig('blacklist', [
  Feature.COMPOSE_MODULE,
]);

export const environment: any = {
  production: true,
  mocks: false,
  providers: <Provider[]> [{
    provide: FeaturesConfig,
    useValue: featuresConfig,
  }],
};
