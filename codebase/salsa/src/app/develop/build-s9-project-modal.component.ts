import { Component, ViewChild } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';

import 'rxjs/add/observable/merge';
import 'rxjs/add/observable/of';
import 'rxjs/add/operator/reduce';

import config from '../config';
import { ModalComponent } from '../core-ui/components/modal.component';
import { IAsset } from '../core/interfaces/common.interface';
import { LibrarySectionDefinition } from '../library/library.interface';
import { ActivityObserver } from '../utils/activity-observer';
import { AppFormGroup } from '../utils/forms';
import { AppValidators } from '../utils/validators';

import { IS9Project} from './s9-project.interfaces';
import { S9ProjectService } from './s9-project.service';
import { IPackageCreate } from './package.interfaces';

@Component({
  selector: 'build-s9-project-modal',
  template: `
    <app-modal
      #modal
      [captionPrefix]="'Build ' + config.asset.labels[IAsset.Type.S9_PROJECT]"
      [buttons]="[{
        'class': 'btn-primary',
        id: '',
        disabled: form.invalid || (_savingObserver.active | async),
        title: 'Build'
      }]"
      (buttonClick)="form.valid && build()"
      [caption]="s9Project?.name">
      <form *ngIf="form">
        <app-input
          [label]="'Package Name'"
          [control]="form.controls.name"
          [disabled]="isPackageNameLocked"
        ></app-input>
        <app-input
          [label]="'Version'"
          [control]="form.controls.version"
          [helpText]="(s9Project && s9Project.packageVersion)
            ? 'Last build version: ' + s9Project.packageVersion
            : null"
        ></app-input>
        <app-description
          [label]="'Description'"
          [control]="form.controls.description"
        ></app-description>
        <app-check
          [label]="'Build Pipeline Operators'"
          [control]="form.controls.analyzePipelineOperators"
        ></app-check>
      </form>
    </app-modal>
  `,
})
export class BuildS9ProjectModalComponent implements LibrarySectionDefinition.BulkModalComponent<IS9Project> {
  IAsset = IAsset;
  config = config;

  s9Project: IS9Project;
  form: AppFormGroup<{
    name: FormControl,
    version: FormControl,
    description: FormControl,
    analyzePipelineOperators: FormControl,
  }>;

  isPackageNameLocked: boolean = false;

  readonly _savingObserver = new ActivityObserver();
  @ViewChild('modal') private modal: ModalComponent;

  constructor(
    public s9ProjectService: S9ProjectService,
  ) {
    this._initForm();
  }

  _initForm() {
    this.form = new AppFormGroup({
      name: new FormControl('', [Validators.required, AppValidators.packageName]),
      version: new FormControl('', [Validators.required, AppValidators.packageVersion]),
      description: new FormControl('', []),
      analyzePipelineOperators: new FormControl(true),
    });
  }

  _resetForm() {
    this.form.reset({
      name: this.s9Project && this.s9Project.packageName || '',
      version: this._nextVersion(this.s9Project && this.s9Project.packageVersion || ''),
      description: '',
      analyzePipelineOperators: true,
    });
  }

  open(items: IS9Project[]) {
    this.s9Project = items[0]; // Can publish only one project at once
    this.isPackageNameLocked = !!(this.s9Project && this.s9Project.packageName);
    this._resetForm();
    return this.modal.show();
  }

  build() {
    const form = this.form.value;
    const data: IPackageCreate = {
      version: form.version,
      analyzePipelineOperators: form.analyzePipelineOperators,
    };
    if (!this.isPackageNameLocked) {
      data['name'] = form.name;
    }

    if (form.description.length) {
      data['description'] = form.description;
    }

    this.s9ProjectService.build(this.s9Project, data);
    this.modal.hide();
  }

  //noinspection JSMethodCanBeStatic
  private _nextVersion(currentVersion: string): string {
    if (!currentVersion) {
      return '0.0.1';
    }

    const sections = currentVersion.split('.');
    const patchVersion = parseInt(sections.pop());

    return `${sections.join('.')}.${patchVersion + 1}`;
  }
}
