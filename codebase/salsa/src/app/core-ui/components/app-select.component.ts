import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  NgZone,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
} from '@angular/core';
import { FormControl } from '@angular/forms';

import * as _ from 'lodash';
import { Subscription } from 'rxjs/Subscription';
require('bootstrap-multiselect');

export interface AppSelectOptionData<T extends AppSelectOptionData.IdType = AppSelectOptionData.IdType> {
  id: T;
  text?: string;
  disabled?: boolean;
  children?: AppSelectOptionData[];
}

interface InternalSelectiOptionDataFlat {
  value: AppSelectOptionData.IdType;
  label: string;
  selected?: boolean;
  disabled?: boolean;
}

interface InternalSelectiOptionData extends InternalSelectiOptionDataFlat {
  children?: InternalSelectiOptionDataFlat[];
}

export namespace AppSelectOptionData {
  export type IdType = string | number;

  export function isOptionData(x: any): x is AppSelectOptionData {
    return x && x.hasOwnProperty('id');
  }

  export const fromList = <T extends IdType>(
    list: T[],
    labels?: string[] | { [key: string]: string },
    disabled?: { [key: string]: boolean },
  ): AppSelectOptionData[] => {
    if (Array.isArray(labels)) {
      return list.map((id, idx): AppSelectOptionData => {
        return { id: id, text: labels[idx] || String(id), disabled: !!(disabled && disabled[String(id)]) };
      });
    } else if (labels) {
      return list.map((id): AppSelectOptionData => {
        return { id: id, text: labels[String(id)] || String(id), disabled: !!(disabled && disabled[String(id)]) };
      });
    } else {
      return list.map((id): AppSelectOptionData => {
        return { id: id, text: String(id), disabled: !!(disabled && disabled[String(id)]) };
      });
    }
  };

  export const fromDict = (dict: {[key: string]: string}): AppSelectOptionData<string>[] => {
    return Object.keys(dict).map((id) => {
      return { id: id, text: dict[id] };
    });
  };
}

export type AppSelectValueType = AppSelectOptionData.IdType | AppSelectOptionData.IdType[];

export type AppSelectOptionsType = (AppSelectOptionData | AppSelectOptionData.IdType)[];

@Component({
  selector: 'app-select',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="form-group p0"
      [formControlValidator]="control"
      [ngClass]="{
        'dropdown-overflow-fix': this.fixOverflowClipping
      }"
    >
      <div class="input-group" [ngClass]="{'has-label': label}">
        <label class="input-group-addon input-group-label"
          *ngIf="label || iconBefore || helpText"
          [ngClass]="{'disabled': disabled || control?.disabled, 'label-bare': !label}">
          <i *ngIf="iconBefore" class="glyphicon" [ngClass]="iconBefore"></i>
          {{label}}<i *ngIf="helpText"
            class="helpText glyphicon glyphicon-question-sign icon-suffix"
            tooltip
            data-toggle="tooltip"
            data-html="true"
            data-placement="top"
            [tooltipTitle]="helpText"
          ></i></label>
        <select [multiple]="multiple"></select>
        <span [ngClass]="{'disabled': disabled || control?.disabled}" class="input-group-addon" (click)="activateSelect($event)">
          <i class="glyphicon" [ngClass]="{'glyphicon-th': multiple, 'glyphicon-chevron-down': !multiple}"></i>
        </span>
      </div>
      <div class="pt5" *ngIf="showErrors && !!control" [formControlValidatorFeedback]="control"></div>
    </div>
  `,
})
export class AppSelectComponent implements OnDestroy, AfterViewInit, OnChanges {
  @Input() value: AppSelectValueType;
  @Input() disabled: boolean = false;
  @Input() showErrors: boolean = true;
  @Input() label: string;
  @Input() multiple: boolean = false;
  @Input() allowNull: boolean = false;
  @Input() placeholder: string = '-Select-';
  @Input() control: FormControl;
  @Input() options: AppSelectOptionsType = [];
  @Input() iconBefore: string;
  @Input() helpText: string;
  @Input() nullSelectedText: string = 'None';
  @Input() fixOverflowClipping: boolean = false;
  @Output() valueChange = new EventEmitter<AppSelectValueType>();
  // refers to http://davidstutz.github.io/bootstrap-multiselect/
  private defaultOptions = {
    maxHeight: 200,
    disableIfEmpty: true,
    includeSelectAllOption: true,
    selectAllJustVisible: false,
    enableClickableOptGroups: false,
    enableCollapsibleOptGroups: true,
    enableFiltering: true,
    includeFilterClearBtn: false,
    filterPlaceholder: 'Search...',
    enableCaseInsensitiveFiltering: true,
    buttonClass: 'btn btn-default btn-select',
    buttonWidth: '100%',
    delimiterText: '; ',
    allSelectedText: 'All Options Selected',
    nonSelectedText: 'No Option Selected',
    //buttonContainer: '<div class="input-group-btn" />',
    templates: {
      filter: '<li class="multiselect-item filter"><div class="search-input-group"><input class="form-control multiselect-search" type="text" /></div></li>',
    },
    onChange: () => {
      this.zone.run(() => {
        this.value = this.getElementValue();
        if (this.control) {
          this.control.markAsDirty();
          this.control.setValue(this.value);
        }
        this.valueChange.emit(this.value);
      });
    },
    onDropdownShow: () => {
      const parent = this.element.parent();

      if (this.fixOverflowClipping) {
        parent.find('.dropdown-menu').css({
          top: parent.position().top + parent.outerHeight() + 1,
        });
      }

      parent.find('.input-group-addon').addClass('active');
    },
    onDropdownHide: () => {
      this.element.parent().find('.input-group-addon').removeClass('active');
    },
  };
  private element: JQuery;
  private controlSubscription: Subscription;

  private _preparedOptions: InternalSelectiOptionData[] = [];
  private _selectionMap: {[k: string]: AppSelectOptionData.IdType} = {};

  constructor(
    private zone: NgZone,
    private el: ElementRef,
  ) {}

  ngOnChanges(changes: SimpleChanges) {
    if (changes.hasOwnProperty('control')) {
      this.controlSubscription && this.controlSubscription.unsubscribe();

      if (this.control) {
        this.controlSubscription = this.control.valueChanges.subscribe((value) => {
          this.value = value;
          this.syncElementValue();
        });
        this.value = this.control.value;
        this.syncElementValue();
      }
    }

    if (changes.hasOwnProperty('options')) {
      this._prepareOptionsMapping();

      this.syncPluginData();

      const selection: AppSelectOptionData.IdType[] = this.multiple
        ? <AppSelectOptionData.IdType[]> this.value
        : this.value ? [<AppSelectOptionData.IdType> this.value] : [];

      if (selection.length && !_.every(selection, _ => !!this._mapValueToSelection(_))) {
        window.setTimeout(() => {
          this.value = this.multiple ? [] : undefined;
          this.control && this.control.setValue(this.value);
          this.syncElementValue();
        });
      }
    }

    if (changes.hasOwnProperty('value')) {
      this.control && this.control.setValue(this.value);
      this.syncElementValue();
    }

    this.syncPluginAvailability();
  }

  ngAfterViewInit() {
    this.element = jQuery(this.el.nativeElement).find('select');
    this.initPlugin();
  }

  ngOnDestroy() {
    this.destroyPlugin();
    this.controlSubscription && this.controlSubscription.unsubscribe();
  }

  activateSelect(event: MouseEvent) {
    event.stopPropagation();
    jQuery(this.el.nativeElement).find('.btn-select').dropdown('toggle');
  }

  private initPlugin() {
    this.destroyPlugin();
    this.zone.runOutsideAngular(() => {
      if (this.element) {
        this.element.multiselect(Object.assign({}, this.defaultOptions, { nonSelectedText: this.placeholder }));
      }
      this.syncElementValue();
    });
    this.syncPluginData();
    this.syncPluginAvailability();
  }

  private syncPluginAvailability() {
    this.zone.runOutsideAngular(() => {
      if (this.element) {
        (this.disabled || (this.control && this.control.disabled))
          ? this.element.multiselect('disable')
          : this.element.multiselect('enable');
      }
    });
  }

  private syncPluginData() {
    this.zone.runOutsideAngular(() => {
      if (this.element && this._preparedOptions) {
        this.element.multiselect('dataprovider', this._preparedOptions);
        this.syncElementValue();
      }
    });
  }

  private destroyPlugin() {
    this.zone.runOutsideAngular(() => {
      if (this.element) {
        this.element.multiselect('destroy');
      }
    });
  }

  private getElementValue(): AppSelectValueType {
    if (!this.element) {
      throw new Error('No element available');
    }

    if (this.multiple) {
      return ((<string[]> this.element.val()) || []).map(_ => this._mapSelectionToValue(_));
    } else {
      return this._mapSelectionToValue(<string> this.element.val());
    }
  }

  private syncElementValue() {
    if (!this.element) {
      return;
    }
    this.zone.runOutsideAngular(() => {
      if (this.multiple) {
        if (Array.isArray(this.value)) {
          const values = this.value.map(_ => this._mapValueToSelection(_));
          this.element.val(values).multiselect('refresh');
        }
      } else {
        const value = this._mapValueToSelection(<AppSelectOptionData.IdType> this.value);
        this.element.val(value).multiselect('refresh');
      }
      this.element.multiselect('refresh');
    });
  }

  // these three functions are here to prepare uniform options for multiselect with string IDs
  private _prepareOptionsMapping() {
    type ReducerResult = [InternalSelectiOptionData[], {[k: string]: AppSelectOptionData.IdType}];
    const reducer = (options: AppSelectOptionsType, prefix: string = ''): ReducerResult => {
      return options.reduce<ReducerResult>(([accOptions, accMap], option, idx) => {
        const id = prefix + String(idx);

        if (AppSelectOptionData.isOptionData(option)) {
          const out: InternalSelectiOptionData = { value: id, label: option.text, disabled: option.disabled };
          accOptions.push(out);
          accMap[id] = option.id;

          if (option.children) {
            const [childrenOptions, childrenMap] = reducer(option.children, `${id}:`);
            out.children = childrenOptions;
            Object.assign(accMap, childrenMap);
          }
        } else {
          accOptions.push({ value: id, label: String(option) });
          accMap[id] = option;
        }

        return [accOptions, accMap];
      }, [[], {}]);
    };

    const options = this.allowNull ? [{ id: null, text: this.nullSelectedText }, ...this.options] : this.options;

    [this._preparedOptions, this._selectionMap] = reducer(options || []);
  }

  private _mapValueToSelection(x: AppSelectOptionData.IdType): string {
    return _.findKey(this._selectionMap, _ => _ === x);
  }

  private _mapSelectionToValue(x: string): AppSelectOptionData.IdType {
    return this._selectionMap[x];
  }
}
