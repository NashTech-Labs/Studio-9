import {
  AfterViewInit,
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

import 'rxjs/add/operator/distinctUntilChanged';
import { Subscription } from 'rxjs/Subscription';
require('icheck');

@Component({
  selector: 'app-check',
  template: `
    <div [ngClass]="{'checkbox': type ==='checkbox', 'radio': type === 'radio'}">
      <label class="ellipsis">
        <input #selector
          [name]="name"
          [value]="value"
          [attr.type]="type"
          [checked]="checked"
          [disabled]="disabled"
          [readonly]="readonly"
        ><span *ngIf="label !== null" class="label-text" [title]="label">{{label}}</span>
        <i *ngIf="helpText"
          class="helpText glyphicon glyphicon-question-sign"
          tooltip
          data-toggle="tooltip"
          data-html="true"
          data-placement="top"
          [tooltipTitle]="helpText"></i>
      </label>
      <div class="clearfix"></div>
    </div>
  `,
})
export class AppCheckComponent implements OnDestroy, AfterViewInit, OnChanges {
  @Input() value: any;
  @Input() disabled: boolean = false;
  @Input() readonly: boolean = false;
  @Input() label: string = null;
  @Input() name: string = 'iCheck' + Math.random().toString();
  @Input() type: 'checkbox' | 'radio' = 'checkbox';
  @Input() checked: boolean = false;
  @Input() control: FormControl = null;
  @Input() helpText: string;
  @Output() checkedChange = new EventEmitter<boolean>();

  @ViewChild('selector') private selector: ElementRef;
  private element: JQuery;
  private defaultOptions = {
    checkboxClass: 'icheckbox_minimal',
    radioClass: 'iradio_minimal',
    increaseArea: '20%',
  };
  private controlSubscription: Subscription;
  private controlStatusSubscription: Subscription;

  ngOnChanges(changes: SimpleChanges) {
    if ('control' in changes) {
      this.controlSubscription && this.controlSubscription.unsubscribe();
      this.controlStatusSubscription && this.controlStatusSubscription.unsubscribe();
      if (this.control) {
        this.disabled = this.control.disabled;
        switch (this.type) {
          case 'radio':
            this.checked = this.control.value === this.value;
            break;
          case 'checkbox':
            this.checked = !!this.control.value;
            break;
        }
        this.controlStatusSubscription = this.control.statusChanges.distinctUntilChanged().subscribe(() => {
          this.disabled = this.control.disabled;
          this.element.prop('disabled', this.disabled);
        });
        this.controlSubscription = this.control.valueChanges.distinctUntilChanged().subscribe((value) => {
          switch (this.type) {
            case 'radio':
              this.checked = value === this.value;
              break;
            case 'checkbox':
              this.checked = !!value;
              break;
          }
          this.syncElementProps();
        });
      }
    }

    if (this.element) {
      if ('checked' in changes || 'disabled' in changes) {
        this.syncElementProps();
      }
      this.initPlugin(); // TODO: remove
    }
  }

  syncElementProps() {
    if (this.element) {
      this.element.prop('checked', this.checked);
      if (this.control) {
        this.disabled = this.control.disabled;
      }
      this.element.prop('disabled', this.disabled);
      this.element.prop('readonly', this.readonly);
      this.element.iCheck('update');
    }
  }

  ngAfterViewInit() {
    this.initPlugin();
  }

  ngOnDestroy() {
    this.controlSubscription && this.controlSubscription.unsubscribe();
    this.destroyPlugin();
  }

  private initPlugin() {
    this.destroyPlugin();
    this.element = jQuery(this.selector.nativeElement);
    this.element.iCheck(this.defaultOptions);
    if (this.type === 'radio') {
      this.element.on('ifChecked', () => {
        this.checkedChange.emit(this.value);
        if (this.control) {
          this.control.markAsDirty();
          this.control.setValue(this.value);
        }
      });
    } else {
      this.element.on('ifToggled', () => {
        this.checked = this.selector.nativeElement.checked;
        if (this.control) {
          this.control.markAsDirty();
          this.control.setValue(this.checked);
        }
        this.checkedChange.emit(this.checked);
      });
    }
  }

  private destroyPlugin() {
    if (this.element) {
      this.element.off('ifToggled');
      this.element.iCheck('destroy');
      this.element.html('');
    }
  }
}
