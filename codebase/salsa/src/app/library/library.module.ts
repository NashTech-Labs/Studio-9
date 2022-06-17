import { ModuleWithProviders, NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';

import { CoreModule } from '../core/core.module';

import { LibraryContextComponent } from './library-context.component';
import { LibraryItemListComponent } from './library-item-list.component';
import { LibraryItemViewComponent } from './library-item-view.component';
import { LibraryViewComponent } from './library.component';
import { ProjectService } from './project.service';

export { LIBRARY_SECTIONS } from './library.interface';

@NgModule({
  imports: [
    RouterModule,
    BrowserModule,
    FormsModule,
    ReactiveFormsModule,
    // deep cortex modules
    CoreModule,
  ],
  declarations: [
    LibraryViewComponent,
    LibraryItemListComponent,
    LibraryContextComponent,
    LibraryItemViewComponent,
  ],
  exports: [
  ],
})
export class LibraryModule {
  static forRoot(): ModuleWithProviders {
    return {
      ngModule: LibraryModule,
      providers: [
        ProjectService,
      ],
    };
  }
}

// Features
declare module '../core/interfaces/feature-toggle.interface' {
  export const enum Feature {
    LIBRARY_MODULE = 'LIBRARY_MODULE',
    LIBRARY_PROJECTS = 'LIBRARY_PROJECTS',
  }
}
