import { Pipe, PipeTransform } from '@angular/core';
import { DomSanitizer, SafeStyle } from '@angular/platform-browser';

import 'rxjs/add/observable/interval';
import 'rxjs/add/operator/startWith';
import { Observable } from 'rxjs/Observable';

import { MiscUtils } from '../utils/misc';

import { CacheService } from './services/cache.service';

const keySequence = ['year', 'month', 'week', 'day', 'hour', 'minute', 'second'];
const
  secondsPer = {
    second: 1,
    minute: 60,
    hour: 3600,
    day: 86400,
    week: 604800,
    month: 2592000,
    year: 31536000,
  },
  getOffset: (date: string) => number = (date: string) => // sec
  Math.floor(Date.now() / 1000) - (Math.floor(Date.parse(date) / 1000));

@Pipe({
  name: 'secondsToTime',
})
export class SecondsToTimePipe implements PipeTransform {
  transform(seconds): string {
    if (!seconds) {
      return '0 seconds';
    }
    return keySequence.reduce((resultString, timeKey) => {
      const count = Math.floor(seconds / secondsPer[timeKey]);
      if (count > 0) {
        const plural = count > 1 ? 's' : '';
        seconds = seconds - secondsPer[timeKey] * count;
        resultString += count.toString() + ' ' + timeKey + plural + ' ';
      }
      return resultString;
    }, '');
  }
}

@Pipe({name: 'dateAgo'})
export class DateAgoPipe implements PipeTransform {

  transform(value: string): Observable<string> {
    // TODO: it seems that observable interval is deleted when pipe is destroyed. it needs clarity about it
    if (value) {
      return Observable.interval(1000).startWith(0).map(() => DateAgoPipe.getLeftTime(value));
    }
    return Observable.empty();
  }

  private static getLeftTime(value: string): string {
    let result: string,
      delta: number   = getOffset(value),
      years: number   = Math.floor(delta / secondsPer.year),
      months: number  = Math.floor((delta % secondsPer.year) / secondsPer.month),
      weeks: number   = Math.floor((delta % secondsPer.month) / secondsPer.week),
      days: number    = Math.floor((delta % secondsPer.week) / secondsPer.day),
      hours: number   = Math.floor((delta % secondsPer.day) / secondsPer.hour),
      minutes: number = Math.floor((delta % secondsPer.hour) / secondsPer.minute);

    // TODO: we can implement float plural -s
    if (years > 0) {
      result = years + ' years' + (months ? ' and ' + months + ' months' : '');
    } else if (months > 0) {
      result = months + ' months' + (weeks ? ' and ' + weeks + ' weeks' : '');
    } else if (weeks > 0) {
      result = weeks + ' weeks' + (days ? ' and ' + days + ' days' : '');
    } else if (days > 0) {
      result = days + ' days';
    } else if (hours > 0) {
      result = hours + ' hours' + (hours < 2 && minutes ? ' and ' + minutes + ' minutes' : '');
    } else if (minutes > 0) {
      result = minutes + ' minutes';
    } else {
      result = 'less than a minute';
    }

    return result + ' ago';
  }
}

@Pipe({name: 'truncate'})
export class TruncatePipe implements PipeTransform {

  // TODO: warning - there is just .toString transformation, so, for null you'll get 'null' string and etc
  // TODO: should user able to use limitTo as 0? Exp "0 || 15" causes 15
  // TODO: should user able to use suffix as ''? In this case suffix will be '...' (default)
  /**
   * Pipe for string truncation.
   * @param value
   * @param args
   *  arg[0] - limitTo value
   *  arg[1] - suffix
   * @returns {string}
   */
  transform(value: string, ...args: any[]): string {
    let valueStr = String(value),
      limitTo = parseInt(args[0]) || 15,
      suffix = args[1] ? String(args[1]) : '...';

    return valueStr.length <= limitTo
      ? valueStr : valueStr.substring(0, limitTo) + suffix;
  }
}

@Pipe({name: 'pluralize'})
export class PluralizePipe implements PipeTransform {
  /**
   *
   * @param value
   * @param label
   * @param plural
   * There are 3 variants of usage:
   *  with label like 'item' -> returns 'item' for 1 and 'items' for other
   *  with label like 'child' and plural like 'children' -> returns 'child' for 1 and 'children' for other
   *  with label like complex object { '0': 'No items', '1': 'Just one', 'other': '{} items' }
   *    -> returns 'No items' for 0
   *    -> returns 'Just one' for 1 (and you can specify any other specific count)
   *    -> returns '8 items' for 8 and etc
   * @returns {String}
   */
  transform(value: number, label: string | Object, plural?: string): string {
    // TODO: some util func for this isObject checking?
    if (typeof label === 'object') {
      let pattern = label[String(value)] ? label[String(value)] : label['other'];
      return (pattern.indexOf('{}') !== -1) ? pattern.replace('{}', value) : pattern;
    }

    // TODO: is it normal that we use plural for 0 count?
    return value === 1 ? <string> label : ( plural ? plural : <string> label + 's');
  }
}

@Pipe({name: 'safeStyle'})
export class SafePipe implements PipeTransform {
  constructor(private sanitizer: DomSanitizer) {
  }

  public transform(value: string): SafeStyle {
    return this.sanitizer.bypassSecurityTrustStyle(value);
  }
}

@Pipe({name: 'map'})
export class MapPipe implements PipeTransform {
  transform<T, R>(value: T[], func: (value: T, ...args: any[]) => R, ...args: any[]): R[] {
    return value ? value.map(_ => func(_, ...args)) : [];
  }
}

@Pipe({name: 'filter'})
export class FilterPipe implements PipeTransform {
  transform<T>(value: T[], func: (value: T, ...args: any[]) => boolean, ...args: any[]): T[] {
    return value ? value.filter(_ => func(_, ...args)) : [];
  }
}

@Pipe({name: 'apply'})
export class ApplyPipe implements PipeTransform {
  transform<T>(value: T, func: (value: T, ...args: any[]) => any, ...args: any[]): any {
    return func(value, ...args);
  }
}

@Pipe({name: 'call'})
export class CallPipe implements PipeTransform {
  transform<T>(func: (...args: any[]) => T, ...args: any[]): T {
    return func(...args);
  }
}

@Pipe({name: 'formatBytes'})
export class FormatBytesPipe implements PipeTransform {
  transform(value: number): string {
    return (typeof value === 'number')
      ? MiscUtils.formatBytes(value)
      : '';
  }
}

@Pipe({name: 'keys'})
export class KeysPipe implements PipeTransform {
  transform<T extends object, K extends keyof T>(value: T): K[] {
    return <any> Object.keys(value);
  }
}

@Pipe({name: 'cache'})
export class CachePipe implements PipeTransform {
  constructor(
    private cache: CacheService,
  ) {}

  transform<T>(value: T, key: string): T {
    const cacheKey = `cachePipe/${key}`;
    return this.cache.get(cacheKey) || this.cache.set(cacheKey, value);
  }
}

@Pipe({ name: 'safeHtml' })
export class SafeHtmlPipe implements PipeTransform {
  constructor(private sanitized: DomSanitizer) {
  }

  transform(value) {
    return this.sanitized.bypassSecurityTrustHtml(value);
  }
}
