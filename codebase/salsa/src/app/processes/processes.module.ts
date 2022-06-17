import { ModuleWithProviders, NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';

import { CoreModule } from '../core/core.module';

import { ProcessContextComponent } from './process-context.component';
import { ProcessesListComponent } from './processes-list.component';

@NgModule({
  imports: [
    RouterModule,
    BrowserModule,
    FormsModule,
    ReactiveFormsModule,
    CoreModule,
  ],
  declarations: [
    ProcessesListComponent,
    ProcessContextComponent,
  ],
})
export class ProcessesModule {
  static forRoot(): ModuleWithProviders {
    return {
      ngModule: ProcessesModule,
      providers: [
      ],
    };
  }
}
