import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
} from '@angular/core';
import { FormControl } from '@angular/forms';

import { Subscription } from 'rxjs/Subscription';

@Component({
  selector: 'app-datepicker',
  template: `
    <div class="form-group p0" [formControlValidator]="control">
      <div class="input-group">
        <label class="input-group-addon input-group-label"
          *ngIf="label || iconBefore || helpText"
          [attr.for]="id"
          [ngClass]="{
            'disabled': disabled || control?.disabled,
            'label-bare': !label,
            'active': isActive
          }"
        >
          <i *ngIf="iconBefore" class="glyphicon" [ngClass]="iconBefore"></i>
          {{label}}
          <i *ngIf="helpText"
            class="helpText glyphicon glyphicon-question-sign icon-suffix"
            tooltip
            data-toggle="tooltip"
            data-html="true"
            data-placement="top"
            [tooltipTitle]="helpText"
          ></i>
        </label>
        <input #selector [id]="id" type="date" class="form-control"
          [ngClass]="{'has-label': label}"
          [disabled]="disabled || control?.disabled"
          [value]="value || ''"
          (input)="onInputChange($event)"
          (focus)="onFocus($event)"
          (blur)="onBlur($event)"
          (keydown)="keyDown.emit($event)"
          (click)="click.emit($event)"
        />
        <span *ngIf="iconAfter" [ngClass]="{'disabled': disabled || control?.disabled, 'clickable': iconAfterClick.observers.length}" class="input-group-addon"
          (click)="onIconAfterClick($event)"
        ><i class="glyphicon" [ngClass]="iconAfter"></i></span>
      </div>
      <div class="pt5" *ngIf="showErrors && !!control" [formControlValidatorFeedback]="control"></div>
    </div>
  `,
})
export class AppDatepickerComponent implements OnChanges, OnDestroy {
  @Input() control: FormControl;
  @Input() label: string = null;
  @Input() value: string = null;
  @Input() disabled: boolean = false;
  @Input() showErrors: boolean = true;
  @Input() iconBefore: string = null;
  @Input() helpText: string;
  @Input() iconAfter: string = 'glyphicon-edit';

  @Output() iconAfterClick = new EventEmitter<MouseEvent>();
  @Output() click = new EventEmitter<MouseEvent>();
  @Output() keyDown: EventEmitter<KeyboardEvent> = new EventEmitter<KeyboardEvent>();
  @Output() focus: EventEmitter<FocusEvent> = new EventEmitter<FocusEvent>();
  @Output() blur: EventEmitter<FocusEvent> = new EventEmitter<FocusEvent>();
  @Output() valueChange: EventEmitter<string> = new EventEmitter<string>();

  id: string;
  isActive: boolean = false;

  private controlSubscription: Subscription;

  constructor(
  ) {
    this.id = 'appdatepicker_' + Math.random().toString();
  }

  ngOnChanges(changes: SimpleChanges) {
    if ('control' in changes) {
      this.controlSubscription && this.controlSubscription.unsubscribe();

      if (this.control) {
        this.value = this.control.value; // when control is set before init view of app-select
        this.controlSubscription = this.control.valueChanges.subscribe((value) => {
          this.value = value;
        });
      }
    }

    if ('value' in changes && this.control) {
      this.control.setValue(this.value);
    }
  }

  onInputChange(event) {
    this.value = event.target.value;
    if (this.control) {
      this.control.markAsDirty();
      this.control.setValue(this.value);
    }
    this.valueChange.emit(this.value);
  }

  onIconAfterClick(event) {
    this.iconAfterClick.emit(event);
  }

  ngOnDestroy() {
    this.controlSubscription && this.controlSubscription.unsubscribe();
  }

  onFocus(event: FocusEvent) {
    this.focus.emit(event);
    this.isActive = true;
  }

  onBlur(event: FocusEvent) {
    this.blur.emit(event);
    this.isActive = false;
  }
}
