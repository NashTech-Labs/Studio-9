import { DecimalPipe } from '@angular/common';
import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  NgZone,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';

import * as $ from 'jquery';
require('jquery-ui/ui/widgets/slider.js');

@Component({
  selector: 'app-slider',
  preserveWhitespaces: false,
  template: `
    <span *ngIf="!!label">
      <span>{{label}}</span>
      <i *ngIf="helpText"
        style="margin-left: 5px"
        class="helpText glyphicon glyphicon-question-sign icon-suffix"
        tooltip
        data-toggle="tooltip"
        data-html="true"
        data-placement="top"
        [tooltipTitle]="helpText"
      ></i>:
    </span>
    <span style="padding:2px;">{{value | apply: _formatValue}}</span>
    <div #selector style="margin:10px;"></div>
  `,
})
export class AppSliderComponent implements OnDestroy, AfterViewInit, OnChanges {
  @Input() value: number[] = [];
  @Input() disabled: boolean = false;
  @Input() label: string = null;
  @Input() options: any = [];
  @Input() min: number = 0;
  @Input() max: number = 10;
  @Input() step: number = 1;
  @Input() range: boolean = false;
  @Input() formatter: (v: number[]) => string;
  @Input() helpText: string;
  @Output() valueChange = new EventEmitter<number[]>();
  @ViewChild('selector') private selector: ElementRef;
  private _decimalPipe: DecimalPipe = new DecimalPipe('en-US');

  constructor(private zone: NgZone) {
  }

  ngOnChanges(changes: SimpleChanges) {
    this.initPlugin();
    // console.log('changes', changes);
    if ('value' in changes) {
      this.value = this.value || [this.min, this.max];
      this.syncElementValue();
    }

    if ('disabled' in changes) {
      if (this.disabled) {
        $(this.selector.nativeElement).slider('disable');
      } else {
        $(this.selector.nativeElement).slider('enable');
      }
    }
  }

  syncElementValue() {
    if (this.selector && typeof this.value !== 'undefined') {
      $(this.selector.nativeElement).slider({ values: this.value });
    }
  }

  ngAfterViewInit() {
    this.initPlugin();
    this.syncElementValue();
  }

  ngOnDestroy() {
    this.destroyPlugin();
  }

  _formatValue = (value: number[]) => {
    if (this.formatter) {
      return this.formatter(value);
    }

    const [_, fraction] = this.step.toString(10).split('.');

    return value
      .map(_ => this._decimalPipe.transform(_, `1.0-${fraction ? fraction.length : 0}`))
      .join(' - ');
  };

  private initPlugin() {
    this.destroyPlugin();
    this.zone.runOutsideAngular(() => {
      $(this.selector.nativeElement).slider({
        range: this.range,
        min: this.min,
        step: this.step,
        max: this.max,
        values: this.value,
        stop: (event, ui) => {
          this.value = ui.values;
          this.valueChange.emit(this.value);
        },
      });
    });
    this.valueChange.emit(this.value);
  }

  private destroyPlugin() {
    this.zone.runOutsideAngular(() => {
      if (this.selector && $(this.selector.nativeElement).slider('instance')) {
        $(this.selector.nativeElement).slider('destroy');
      }
    });
  }
}
