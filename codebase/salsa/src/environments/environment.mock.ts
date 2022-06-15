import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { Provider } from '@angular/core';

import { FixtureService } from '../app/fixture/fixture.service';

export const environment: any = {
  production: false,
  mocks: true,
  providers: <Provider[]> [
    {
      provide: HTTP_INTERCEPTORS,
      useClass: FixtureService,
      multi: true,
    },
  ],
};
