import { Component, ComponentFactoryResolver, Inject, OnDestroy, ViewContainerRef } from '@angular/core';
import { ComponentRef } from '@angular/core/src/linker/component_factory';
import { ActivatedRoute, Router } from '@angular/router';

import * as _ from 'lodash';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { IAsset } from '../core/interfaces/common.interface';

import { LIBRARY_SECTIONS, LibrarySectionDefinition } from './library.interface';

@Component({
  selector: 'library-item-view',
  template: ' ',
})
export class LibraryItemViewComponent implements OnDestroy {
  readonly sections: {[assetType: string]: LibrarySectionDefinition<IAsset>} = {};
  private routeSubscription: Subscription;
  private componentReference: ComponentRef<any>;

  constructor(
    private viewContainer: ViewContainerRef,
    private componentFactoryResolver: ComponentFactoryResolver,
    private route: ActivatedRoute,
    private router: Router,
    @Inject(LIBRARY_SECTIONS) sections: LibrarySectionDefinition<IAsset>[],
  ) {
    this.sections = _.keyBy(sections, (_): string => config.asset.aliasesPlural[_.assetType]);

    this.routeSubscription = this.route.params.subscribe(params => {
      const assetType = params['type'];
      const assetId = params['itemId'];

      const section = this.sections[assetType];

      if (this.componentReference) {
        this.componentReference.destroy();
        this.componentReference = null;
      }

      if (!section || !section.viewComponent) {
        this.router.navigate([...section.baseRoute, assetId]);
      } else {
        const factory = this.componentFactoryResolver.resolveComponentFactory(section.viewComponent);
        this.componentReference = this.viewContainer.createComponent(factory);
      }
    });
  }

  ngOnDestroy() {
    this.routeSubscription.unsubscribe();
    if (this.componentReference) {
      this.componentReference.destroy();
      this.componentReference = null;
    }
  }
}
