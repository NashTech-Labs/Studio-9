import { Injectable } from '@angular/core';
import { CanDeactivate } from '@angular/router';

import { HasUnsavedData } from './core.interface';

@Injectable()
export class HasUnsavedDataGuard implements CanDeactivate<HasUnsavedData> {
  canDeactivate(component: HasUnsavedData): boolean {
    if (component.hasUnsavedData && component.hasUnsavedData()) {
      return confirm('You have some unsaved form data. Are you sure you want to lose it and go to another page?');
    }
    return true;
  }
}
