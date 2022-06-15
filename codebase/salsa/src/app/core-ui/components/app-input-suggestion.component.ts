import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { FormControl } from '@angular/forms';

import { TypeaheadMatch } from 'ngx-bootstrap';
import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';
import { Observer } from 'rxjs/Observer';
import { Subscription } from 'rxjs/Subscription';

import { ReactiveLoader } from '../../utils/reactive-loader';

export interface ISuggestionsLoader {
  fn: Function;
  args: any[];
}

// @TODO: should we merge typeahead functionality with app-input component?

@Component({
  selector: 'app-input-suggestion',
  template: `
    <div class="form-group p0" [formControlValidator]="control">
      <div class="input-group" [formGroup]="control.parent">
        <label class="input-group-addon input-group-label"
          *ngIf="label || iconBefore"
          [attr.for]="id"
          [ngClass]="{'disabled': control.disabled || disabled, 'label-bare': iconBefore}">
          <i *ngIf="iconBefore" class="glyphicon" [ngClass]="iconBefore"></i>
          {{label}}</label
        >
        <input
          class="form-control"
          formControlName="value"
          activateGroup
          [id]="id"
          [attr.type]="type"
          [attr.autocomplete]="autocompleteMode || ''"
          [readonly]="readonly"
          [ngClass]="{'has-label': label}"
          [typeahead]="dataSource"
          [typeaheadMinLength]="0"
          [typeaheadOptionsLimit]="20"
          [typeaheadOptionField]="optionField || null"
          [typeaheadWaitMs]="100"
          (typeaheadOnSelect)="onSelect.emit($event)"
          (blur)="blur.emit($event)"
          (keydown)="keydown.emit($event)"
          (focus)="focus.emit($event)"
          (input)="domInput.emit($event)"
          (click)="click.emit($event)">
        <span class="input-group-addon"
          [ngClass]="{'disabled': control.disabled || disabled}"
          (click)="onIconAfterClick($event)">
          <i class="glyphicon" [ngClass]="iconAfter">
            <app-spinner [visibility]="!suggestionsLoader.loaded && firstRequestSend"
              [height]="34" [backgroundColor]="'#e2e6ef'" [color]="'#ffc65c'"></app-spinner>
          </i>
        </span>
      </div>
      <div class="pt5" *ngIf="showErrors" [formControlValidatorFeedback]="control"></div>
    </div>
  `,
})
export class AppInputSuggestionComponent implements OnChanges, OnDestroy {
  @Input() control: FormControl;
  @Input() suggestions: ISuggestionsLoader;
  @Input() label: string = null;
  @Input() value: any = null;
  @Input() type: string = 'text'; //text or number
  @Input() disabled: boolean = false;
  @Input() showErrors: boolean = true;
  @Input() readonly: boolean = false;
  @Input() iconBefore: string = null;
  @Input() iconAfter: string = 'glyphicon-edit';
  @Input() autocompleteMode: string = ''; // Introduced to support non-standard autocomplete values
  @Input() optionField: string;
  @Input() useInternalCache: boolean = true;
  @Output() iconAfterClick = new EventEmitter<any>();
  @Output() click = new EventEmitter<any>();
  @Output() keydown = new EventEmitter<any>();
  @Output() focus = new EventEmitter<any>();
  @Output() blur = new EventEmitter<any>();
  @Output() onSelect = new EventEmitter<TypeaheadMatch>();
  @Output() domInput = new EventEmitter<any>();
  id: string;

  dataSource: Observable<any>;
  firstRequestSend: boolean = false;

  readonly suggestionsLoader: ReactiveLoader<string[], string>;

  private controlSubscription: Subscription;
  private typeaheadObserver: Observer<string[]>;
  private cachedSuggestions: string[];
  private cachedSuggestionsLoaderArgs: any[];
  private cachedQuery: string;

  constructor() {
    this.id = 'appinputsuggestion_' + Math.random().toString();

    this.suggestionsLoader = new ReactiveLoader((query: string) => {
      if (this.useInternalCache && query === this.cachedQuery && this.cachedSuggestionsLoaderArgs === this.suggestions.args && this.cachedSuggestions) {
        return Observable.of(this.cachedSuggestions);
      } else {
        this.firstRequestSend = true;
        return this.suggestions.fn(query, ...this.suggestions.args);
      }
    });

    this.suggestionsLoader.subscribe((suggestions: string[]) => {
      this.cachedQuery = this.control.value;
      this.cachedSuggestions = suggestions;
      this.cachedSuggestionsLoaderArgs = this.suggestions.args;
      this.typeaheadObserver && this.typeaheadObserver.next(suggestions);
    });

    // note that 'dataSource' for [typeahead] should be created before initializing of typeahead, in constructor
    this.dataSource = Observable.create(observer => {
      this.typeaheadObserver = observer;
      this.suggestionsLoader.load(this.control.value);
    });
  }

  ngOnChanges(changes: SimpleChanges) {
    if ('control' in changes && this.control) {
      this.value = this.control.value; // when control is set before init view of app-select

      this.controlSubscription = this.control.valueChanges.subscribe((value) => {
        this.value = value;
      });
    } else if (!this.control) {
      this.control = new FormControl({ value: this.value, disabled: this.disabled });
    }

    if ('value' in changes) {
      this.control.setValue(this.value);
    }

    if ('disabled' in changes) {
      this.disabled ? this.control.disable() : this.control.enable();
    }
  }

  onIconAfterClick(event) {
    this.iconAfterClick.emit(event);
  }

  ngOnDestroy() {
    this.controlSubscription && this.controlSubscription.unsubscribe();
  }
}
