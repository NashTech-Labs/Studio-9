import { Pipe, PipeTransform } from '@angular/core';

@Pipe({name: 'diaaDecileRange'})
export class DecileRangePipe implements PipeTransform {
  transform([min, max]: [number, number]): string {
    return `${(min - 1) * 10}% to ${max * 10}%`;
  }
}
