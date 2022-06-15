import {
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import { FormControl } from '@angular/forms';

import { Subscription } from 'rxjs/Subscription';

@Component({
  selector: 'app-input',
  preserveWhitespaces: false,
  template: `
    <div class="form-group p0" [formControlValidator]="control">
      <div class="input-group">
        <label class="input-group-addon input-group-label"
          *ngIf="label || iconBefore || helpText"
          [attr.for]="id"
          [ngClass]="{'disabled': disabled || control?.disabled, 'label-bare': !label}">
          <i *ngIf="iconBefore" class="glyphicon" [ngClass]="iconBefore"></i>
          <span>{{label}}</span>
          <i *ngIf="helpText"
            class="helpText glyphicon glyphicon-question-sign icon-suffix"
            tooltip
            data-toggle="tooltip"
            data-html="true"
            data-placement="top"
            [tooltipTitle]="helpText"
          ></i>
        </label>
        <input
          class="form-control"
          activateGroup
          #selector
          [id]="id"
          [attr.type]="type"
          [attr.step]="step"
          [attr.autocomplete]="autocompleteMode || ''"
          [ngClass]="{'has-label': label}"
          [readonly]="readonly"
          [disabled]="disabled || control?.disabled"
          [placeholder]="placeholder"
          [min]="min"
          [max]="max"
          [value]="(value | apply: isDefined) ? value : ''"
          (input)="onInputChange()"
          (blur)="blur.emit($event)"
          (keydown)="keyDown.emit($event)"
          (focus)="focus.emit($event)"
          (click)="click.emit($event)"
        />
        <span
          *ngIf="iconAfter"
          class="input-group-addon"
          [ngClass]="{'disabled': disabled || control?.disabled, 'clickable': iconAfterClick.observers.length}"
          (click)="onIconAfterClick($event)"
        >
          <i class="glyphicon" [ngClass]="iconAfter"></i>
        </span>
      </div>
      <div class="pt5" *ngIf="showErrors && !!control" [formControlValidatorFeedback]="control"></div>
    </div>
  `,
})
export class AppInputComponent implements OnChanges, OnDestroy {
  @Input() control: FormControl;
  @Input() label: string = null;
  @Input() value: any = null;
  @Input() type: 'text' | 'number' = 'text'; //text or number
  @Input() step: number = null;
  @Input() disabled: boolean = false;
  @Input() showErrors: boolean = true;
  @Input() readonly: boolean = false;
  @Input() iconBefore: string = null;
  @Input() helpText: string;
  @Input() iconAfter: string = 'glyphicon-edit';
  @Input() placeholder: string = '';
  @Input() min: number;
  @Input() max: number;
  @Input() autocompleteMode: string = ''; // Introduced to support non-standard autocomplete values
  @Output() iconAfterClick = new EventEmitter<any>();

  @Output() click = new EventEmitter<any>();
  @Output() keyDown: EventEmitter<any> = new EventEmitter<any>();
  @Output() focus: EventEmitter<any> = new EventEmitter<any>();
  @Output() blur: EventEmitter<any> = new EventEmitter<any>();
  @Output() valueChange: EventEmitter<any> = new EventEmitter<any>();
  id: string;

  @ViewChild('selector') private selector: ElementRef;
  private controlSubscription: Subscription;

  constructor(
  ) {
    this.id = 'appinput_' + Math.random().toString();
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

  onInputChange() {
    const controlValue = jQuery(this.selector.nativeElement).val();
    if (this.type === 'number' && typeof controlValue === 'string') {
      this.value = controlValue.length ? +controlValue : null;
    } else {
      this.value = controlValue;
    }

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

  protected isDefined(value: any): boolean {
    return typeof(value) === 'string' || typeof(value) === 'number';
  }
}

