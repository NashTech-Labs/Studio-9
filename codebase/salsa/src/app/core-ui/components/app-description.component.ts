import { Component, Input } from '@angular/core';
import { FormControl } from '@angular/forms';

@Component({
  selector: 'app-description',
  template: `
    <div class="form-group p0">
      <div class="input-group">
        <label
          class="input-group-addon input-group-label"
          [ngClass]="{'disabled': (disabled || control?.disabled), 'text': !editMode}"
          [attr.for]="id"
          [innerText]="label"
        ></label>
        <div *ngIf="!editMode" showMore class="form-control text has-label">{{controlValue}}</div>
        <textarea
          *ngIf="editMode"
          class="form-control has-label"
          autosize
          activateGroup
          [id]="id"
          [placeholder]="placeholder"
          [formControl]="control"
          [value]="controlValue"
          [readonly]="readonly"
          [attr.disabled]="(disabled || control?.disabled) ? 'disabled' : null"
        ></textarea>
        <span
          *ngIf="!disabled"
          class="input-group-addon btn btn-primary edit-text"
          (click)="toggleEditMode()"
        >
          <i
            class="glyphicon"
            [ngClass]="{'glyphicon-pencil': !editMode, 'glyphicon-eye-open': editMode}"
          ></i>
        </span>
      </div>
    </div>
  `,
})
export class AppDescriptionComponent {
  @Input() control: FormControl;
  @Input() value: any = null;
  @Input() disabled: boolean = false;
  @Input() editMode = false;
  @Input() label: string = 'Description';
  @Input() placeholder = 'Type Description';
  @Input() readonly: boolean = false;

  id: string;

  constructor(
  ) {
    this.id = 'appdescription_' + Math.random().toString();
  }

  get controlValue(): string {
    return this.control ? this.control.value : this.value;
  }

  toggleEditMode() {
    this.editMode = !this.editMode;
  }
}
