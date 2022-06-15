import { Directive, Input, TemplateRef, ViewContainerRef } from '@angular/core';

import * as _ from 'lodash';

import { Feature } from '../interfaces/feature-toggle.interface';
import { FeatureToggleService } from '../services/feature-toggle.service';

@Directive({ selector: '[featureToggle]' })
export class FeatureToggleDirective {
  private hasContent = false;

  constructor(
    private service: FeatureToggleService,
    private templateRef: TemplateRef<any>,
    private viewContainer: ViewContainerRef,
  ) {}

  @Input() set featureToggle(feature: Feature | Feature[]) {
    const showContent = Array.isArray(feature)
      ? _.every(feature, _ => this.service.isFeatureEnabled(_))
      : this.service.isFeatureEnabled(feature);

    if (showContent && !this.hasContent) {
      this.viewContainer.createEmbeddedView(this.templateRef);
      this.hasContent = true;
    } else if (!showContent && this.hasContent) {
      this.viewContainer.clear();
      this.hasContent = false;
    }
  }

}
