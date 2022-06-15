import { APP_BASE_HREF } from '@angular/common';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { ErrorHandler, NgModule, PlatformRef, enableProdMode } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';

import { TypeaheadModule } from 'ngx-bootstrap/typeahead';

import { environment } from '../environments/environment';

import { AlbumsModule } from './albums/albums.module';
import { AppComponent } from './app.component';
import { APP_ROUTER_PROVIDERS, routing } from './app.routes';
import { AppBlockComponent } from './components/app-block.component';
import { EmailConfirmationComponent } from './components/email-confirmation.component';
import { LayoutDeskHeaderComponent } from './components/layout-desk-header.component';
import { LayoutDeskComponent } from './components/layout-desk.component';
import { LayoutSplashComponent } from './components/layout-splash.component';
import { MyPasswordChangeModalComponent } from './components/my-password-change-modal.component';
import { NotificationListComponent } from './components/notification-list.component';
import { NotificationComponent } from './components/notification.component';
import { SigninPasswordConfirmationComponent } from './components/signin-password-confirmation.component';
import { SigninPasswordComponent } from './components/signin-password.component';
import { SigninComponent } from './components/signin.component';
import { SignOutComponent } from './components/signout.component';
import { SignupComponent } from './components/signup.component';
import { ComposeModule } from './compose/compose.module';
import { CoreUIModule } from './core-ui/core-ui.module';
import { CoreModule } from './core/core.module';
import { DatasetModule } from './datasets/dataset.module';
import { DeployModule } from './deploy/deploy.module';
import { DevelopModule } from './develop/develop.module';
import { DIAAModule } from './diaa/diaa.module';
import { ExperimentsModule } from './experiments/experiments.module';
import { LibraryModule } from './library/library.module';
import { OptimizeModule } from './optimize/optimize.module';
import { PipelineModule } from './pipelines/pipeline.module';
import { PlayModule } from './play/play.module';
import { ProcessesModule } from './processes/processes.module';
import { AclService } from './services/acl.service';
import { TablesModule } from './tables/tables.module';
import './test-experiment/test-experiment.module';
import { TrainModule } from './train/train.module';
import { UsersModule } from './users/users.module';
import { VisualizeModule } from './visualize/visualize.module';

if (environment.production) {
  enableProdMode();
}

class AppErrorHandler implements ErrorHandler {
  constructor(private platformRef: PlatformRef) {}
  handleError(error) {
    if (error.message) {
      console.error(error.message);
    }
    console.error(error);
    this.platformRef.destroyed || this.platformRef.destroy();
  }
}

@NgModule({
  imports: [
    BrowserModule,
    HttpClientModule,
    FormsModule,
    ReactiveFormsModule,
    routing,
    TypeaheadModule.forRoot(),
    // deep cortex modules
    CoreUIModule.forRoot(),
    CoreModule.forRoot(),
    LibraryModule.forRoot(),
    AlbumsModule.forRoot(),
    TablesModule.forRoot(),
    DatasetModule.forRoot(),
    ComposeModule.forRoot(),
    ExperimentsModule.forRoot(),
    TrainModule.forRoot(),
    PlayModule.forRoot(),
    VisualizeModule.forRoot(),
    OptimizeModule.forRoot(),
    DIAAModule.forRoot(),
    DeployModule.forRoot(),
    ProcessesModule.forRoot(),
    PipelineModule.forRoot(),
    DevelopModule.forRoot(),
    UsersModule.forRoot(),
  ],
  providers: [
    ...(environment.providers || []),
    {
      provide: ErrorHandler,
      deps: [PlatformRef],
      useFactory: (platformRef: PlatformRef) => new AppErrorHandler(platformRef),
    },
    APP_ROUTER_PROVIDERS,
    {
      provide: APP_BASE_HREF,
      useValue: '/',
    },
    HttpClient,
    AclService,
  ],
  declarations: [
    AppComponent,
    AppBlockComponent,
    // Components from Routes
    SignupComponent,
    SigninComponent,
    SignOutComponent,
    SigninPasswordComponent,
    SigninPasswordConfirmationComponent,
    LayoutDeskComponent,
    LayoutDeskHeaderComponent,
    LayoutSplashComponent,
    NotificationListComponent,
    NotificationComponent,
    MyPasswordChangeModalComponent,
    EmailConfirmationComponent,
  ],
  bootstrap: [AppComponent],
})
export class AppModule {}
