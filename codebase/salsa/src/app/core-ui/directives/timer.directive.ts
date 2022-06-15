import { Directive, ElementRef, Input, NgZone, OnChanges, OnDestroy, OnInit } from '@angular/core';

@Directive({
  selector: 'timer',
})
export class TimerDirective implements OnInit, OnChanges, OnDestroy {
  @Input() start: string; // start datetime
  SECONDS = {
    minute: 60,
    hour: 60 * 60,
    day: 24 * 60 * 60,
  };

  private el: HTMLElement;
  private intervalId: number;

  constructor(
    el: ElementRef,
    private zone: NgZone,
  ) {
    this.el = el.nativeElement;
  }

  ngOnInit() {
    this.zone.runOutsideAngular(() => {
      this.intervalId = window.setInterval(() => {
        this._updateTimer();
      }, 1000);
    });
  }

  ngOnChanges() {
    this._updateTimer();
  }

  ngOnDestroy() {
    this.intervalId && window.clearInterval(this.intervalId);
  }

  private _getOffset(): number {
    return this.start ? Math.floor((Date.now() - Date.parse(this.start)) / 1000) : 0;
  }

  private _updateTimer() {
    const offset = this._getOffset();
    const
      days = Math.floor(offset / this.SECONDS.day),
      hours = ('0' + Math.floor((offset % this.SECONDS.day) / this.SECONDS.hour)).slice(-2),
      minutes = ('0' + Math.floor((offset % this.SECONDS.hour) / this.SECONDS.minute)).slice(-2),
      seconds = ('0' + offset % this.SECONDS.minute).slice(-2);

    let result = `${hours}:${minutes}:${seconds}`;

    if (days > 0) {
      result = `${days} day${days > 1 ? 's' : ''} ${result}`;
    }

    this.el.innerHTML = result;
  }
}
