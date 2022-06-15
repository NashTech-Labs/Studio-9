import { ModuleWithProviders, NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';

import { DndModule } from 'ng2-dnd';
import { TypeaheadModule } from 'ngx-bootstrap/typeahead';

import { HasUnsavedDataGuard } from '../core/core.guards';

import { AppCheckComponent } from './components/app-check.component';
import { AppDatepickerComponent } from './components/app-datepicker.component';
import { AppDescriptionComponent } from './components/app-description.component';
import { AppFakeControlComponent } from './components/app-fake-control.component';
import { AppFormGroupComponent } from './components/app-form-group.component';
import { AppInputSuggestionComponent } from './components/app-input-suggestion.component';
import { AppInputComponent } from './components/app-input.component';
import { AppSelectComponent } from './components/app-select.component';
import { AppSliderComponent } from './components/app-slider.component';
import { AppTextareaComponent } from './components/app-textarea.component';
import { ModalComponent } from './components/modal.component';
import { PaginationComponent } from './components/pagination.component';
import { SortColumnsComponent } from './components/sort-columns.component';
import { SpinnerComponent } from './components/spinner.component';
import { TabsComponent } from './components/tabs.component';
import {
  ApplyPipe,
  CachePipe,
  CallPipe,
  DateAgoPipe,
  FilterPipe,
  FormatBytesPipe,
  KeysPipe,
  MapPipe,
  PluralizePipe,
  SafeHtmlPipe,
  SafePipe,
  SecondsToTimePipe,
  TruncatePipe,
} from './core-ui.pipes';
import { ActivateGroupDirective } from './directives/activate-group.directive';
import { AdaptiveHeightDirective } from './directives/adaptive-height.directive';
import { CaptureDirective } from './directives/capture.directive';
import { CodeDirective } from './directives/code.directive';
import { DragImageDirective } from './directives/dnd.directive';
import { DropDownContextDirective } from './directives/dropdown-context.directive';
import { DropDownDirective } from './directives/dropdown.directive';
import { FormControlValidatorFeedbackDirective } from './directives/form-control-validator-feedback.directive';
import { FormControlValidatorDirective } from './directives/form-control-validator.directive';
import { GridSortDirective } from './directives/grid-sort.directive';
import { IfOnceDirective } from './directives/if-once.directive';
import { VarDirective } from './directives/ng-var.directive';
import { PreventDefaultClickDirective } from './directives/prevent-default.directive';
import { ShowMoreDirective } from './directives/show-more.directive';
import { StickyHeaderDirective } from './directives/sticky-header.directive';
import { TextareaAutosizeDirective } from './directives/textarea-autosize.directive';
import { TimerDirective } from './directives/timer.directive';
import { TooltipDirective } from './directives/tooltip.directive';
import { CacheService } from './services/cache.service';
import { DropdownContextService } from './services/dropdown-context.service';
import { ModalService } from './services/modal.service';

let CORE_UI_DECLARATIONS = [
  // Components
  ModalComponent,
  SpinnerComponent,
  PaginationComponent,
  TabsComponent,
  AppSelectComponent,
  AppFormGroupComponent,
  AppInputComponent,
  AppTextareaComponent,
  AppInputSuggestionComponent,
  AppCheckComponent,
  AppSliderComponent,
  AppTextareaComponent,
  SortColumnsComponent,
  AppFakeControlComponent,
  AppDatepickerComponent,
  AppDescriptionComponent,
  // Pipes
  SafePipe,
  PluralizePipe,
  TruncatePipe,
  DateAgoPipe,
  MapPipe,
  ApplyPipe,
  CallPipe,
  FormatBytesPipe,
  FilterPipe,
  KeysPipe,
  CachePipe,
  SecondsToTimePipe,
  SafeHtmlPipe,
  // Directives
  FormControlValidatorDirective,
  FormControlValidatorFeedbackDirective,
  CodeDirective,
  TimerDirective,
  DragImageDirective,
  DropDownDirective,
  DropDownContextDirective,
  PreventDefaultClickDirective,
  TooltipDirective,
  AdaptiveHeightDirective,
  StickyHeaderDirective,
  GridSortDirective,
  CaptureDirective,
  IfOnceDirective,
  VarDirective,
  ShowMoreDirective,
  ActivateGroupDirective,
  TextareaAutosizeDirective,
];

@NgModule({
  imports: [
    RouterModule,
    BrowserModule,
    FormsModule,
    ReactiveFormsModule,
    DndModule,
    TypeaheadModule,
  ],
  declarations: CORE_UI_DECLARATIONS,
  exports: [
    CORE_UI_DECLARATIONS,
    DndModule,
  ],
})
export class CoreUIModule {
  static forRoot(): ModuleWithProviders {
    return {
      ngModule: CoreUIModule,
      providers: [
        DropdownContextService,
        ModalService,
        CacheService,
        ...DndModule.forRoot().providers,
        HasUnsavedDataGuard,
      ],
    };
  }
}
