import { ModuleWithProviders, NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';

import { CoreModule } from '../core/core.module';

import { UserContextComponent } from './user-context.component';
import { UserDeleteModalComponent } from './user-delete-modal';
import { UserEditComponent } from './user-edit.component';
import { UserListComponent } from './user-list.component';
import { UserManagementService } from './user-management.service';
import { UserOperationsComponent } from './user-operations.component';

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
    UserContextComponent,
    UserListComponent,
    UserEditComponent,
    UserDeleteModalComponent,
    UserOperationsComponent,
  ],
  exports: [
  ],
})
export class UsersModule {
  static forRoot(): ModuleWithProviders {
    return {
      ngModule: UsersModule,
      providers: [
        UserManagementService,
      ],
    };
  }
}
