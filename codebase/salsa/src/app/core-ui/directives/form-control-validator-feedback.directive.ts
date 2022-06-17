import { Directive, ElementRef, Input, OnChanges, OnDestroy } from '@angular/core';
import { FormControl } from '@angular/forms';

import { Subscription } from 'rxjs/Subscription';

import config from '../../config';

@Directive({ selector: '[formControlValidatorFeedback]' })
export class FormControlValidatorFeedbackDirective implements OnChanges, OnDestroy {
  @Input('formControlValidatorFeedback') control: FormControl;

  private readonly el: HTMLElement;
  private subscription: Subscription;

  constructor(el: ElementRef) {
    this.el = el.nativeElement;
  }

  ngOnChanges() {
    this.subscription && this.subscription.unsubscribe();

    if (this.control) {
      this.subscription = this.control.valueChanges.subscribe(() => {
        this._updateContent();
      });
    }

    this._updateContent();
  }

  ngOnDestroy(): void {
    this.subscription && this.subscription.unsubscribe();
  }

  private _updateContent(): void {
    const $el = $(this.el);

    if (this.control && this.control.invalid && !this.control.pristine) {
      const errors: string[] = Object.keys(this.control.errors || {}); // @todo sort by priority

      const key = errors.filter(_ => _ in config.validatorErrors).shift();

      if (key) {
        let message = config.validatorErrors[key].replace('%value%', this.control.errors[key].actualValue);
        Object.keys(this.control.errors[key]).forEach(metaKey => {
          message = message.replace(`%${metaKey}%`, this.control.errors[key][metaKey]);
        });
        $el.html(`<div class="alert alert-danger">${message}</div>`).show();
      } else {
        $el.html('').hide();
      }
    } else {
      $el.html('').hide();
    }
  }
}
