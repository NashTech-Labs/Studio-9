import { inject } from '@angular/core/testing';
import { DomSanitizer } from '@angular/platform-browser';

import { Observable } from 'rxjs/Observable';

import {
  ApplyPipe,
  DateAgoPipe,
  FilterPipe,
  FormatBytesPipe,
  KeysPipe,
  MapPipe,
  PluralizePipe,
  SafePipe,
  TruncatePipe,
} from './core-ui.pipes';

describe('TruncatePipe', () => {
    let pipe = new TruncatePipe();

    it('should return when nothing to truncate', () => {
      expect(pipe.transform('Truncate me!')).toBe('Truncate me!');
    });

    it('should return truncated string by exact limitTo with default suffix', () => {
        expect(pipe.transform('Truncate me!', 5)).toBe('Trunc...');
    });

    it('should return original string when limitTo is over than string length', () => {
        expect(pipe.transform('Truncate me!', 50)).toBe('Truncate me!');
    });

    it('should return default length truncated text', () => {
      expect(pipe.transform('Truncate me please!')).toBe('Truncate me ple...');
    });

    it('should return non-default truncation suffix', () => {
      expect(pipe.transform('Truncate me please!', null, '!!!')).toBe('Truncate me ple!!!');
    });
});


describe('PluralizePipe', () => {
  let pipe: PluralizePipe;

  beforeEach(() => {
    pipe = new PluralizePipe();
  });

  it('should transform to No albums', () => {
    expect(pipe.transform(0, { other: '{} albums', '0': 'No albums', '1': '{} album' })).toEqual('No albums');
  });

  it('should transform to 1 album', () => {
    expect(pipe.transform(1, { other: '{} albums', '0': 'No albums', '1': '{} album' })).toEqual('1 album');
  });

  it('should transform to 2 albums', () => {
    expect(pipe.transform(2, { other: '{} albums', '0': 'No albums', '1': '{} album' })).toEqual('2 albums');
  });

  it('should transform to 0 as albums', () => {
    expect(pipe.transform(0, 'album', 'albums')).toEqual('albums');
  });

  it('should transform to 1 as album', () => {
    expect(pipe.transform(1, 'album', 'albums')).toEqual('album');
  });

  it('should transform to 2 as albums', () => {
    expect(pipe.transform(2, 'album', 'albums')).toEqual('albums');
  });

  it('should transform album+s', () => {
    expect(pipe.transform(2, 'album')).toEqual('albums');
  });
});

describe('SafePipe', () => {
  let pipe: SafePipe;
  let sanitizer: DomSanitizer;

  beforeEach(inject([DomSanitizer], (sanitizerInstance) => {
    pipe  = new SafePipe(sanitizerInstance);
    sanitizer = sanitizerInstance;
  }));

  it('should transform safe style', () => {
    expect(pipe.transform('width: 20%')).toEqual(sanitizer.bypassSecurityTrustStyle('width: 20%'));
  });
});

describe('MapPipe', () => {
  let pipe: MapPipe;

  beforeEach(() => {
    pipe = new MapPipe();
  });

  it('should map no values', () => {
    expect(pipe.transform(null, () => {})).toEqual([]);
  });

  it('should map values', () => {
    expect(pipe.transform([{ id: 1, text: 'one' }, { id: 2, text: 'two' }], (item) => {
      return {
        foo: item.id,
        bar: item.text + 's',
      };
    })).toEqual([{ foo: 1, bar: 'ones' }, { foo: 2, bar: 'twos' }]);
  });
});

describe('ApplyPipe', () => {
  let pipe: ApplyPipe;

  beforeEach(() => {
    pipe = new ApplyPipe();
  });

  it('should map no values', () => {
    const applySpy = jasmine.createSpy('applySpy');
    const value = 'Specific Value';
    const anotherValue = 'Another Specific Value';
    //first call
    pipe.transform(value, applySpy);
    expect(applySpy).toHaveBeenCalledTimes(1);
    expect(applySpy.calls.first().args[0]).toEqual(value);
    //second call
    pipe.transform(anotherValue, applySpy, value);
    expect(applySpy).toHaveBeenCalledTimes(2);
    expect(applySpy.calls.mostRecent().args[0]).toEqual(anotherValue);
    expect(applySpy.calls.mostRecent().args[1]).toEqual(value);
  });
});

describe('FilterPipe', () => {
  let pipe: FilterPipe;

  beforeEach(() => {
    pipe = new FilterPipe();
  });

  it('should filter no values', () => {
    expect(pipe.transform(null, () => {
      return true;
    })).toEqual([]);
  });

  it('should filter values', () => {
    expect(pipe.transform([{ id: 1, text: 'one' }, { id: 2, text: 'two' }], (item) => item.id === 1)).toEqual([{
      id: 1,
      text: 'one',
    }]);
  });
});

describe('FormatBytesPipe', () => {
  let pipe: FormatBytesPipe;

  beforeEach(() => {
    pipe = new FormatBytesPipe();
  });

  it('should map no values', () => {
    expect(pipe.transform(0)).toEqual('0 Bytes');
    expect(pipe.transform(5)).toEqual('5 Bytes');
    expect(pipe.transform(1024)).toEqual('1 KB');
    expect(pipe.transform(2048)).toEqual('2 KB');
  });
});

describe('DateAgoPipe', () => {
  let pipe: DateAgoPipe;

  beforeEach(() => {
    pipe = new DateAgoPipe();
  });

  it('should transform null string-date', () => {
    const result: Observable<string> = pipe.transform(null);
    const successSpy = jasmine.createSpy('successSpy');
    result.subscribe(successSpy);
    expect(successSpy).not.toHaveBeenCalled();
  });

  it('should transform string-date 5 minutes ago', () => {
    const delta = Date.now() - 5 * 60 * 1000;
    const result: Observable<string> = pipe.transform(new Date(delta).toString());
    const successSpy = jasmine.createSpy('successSpy');
    result.subscribe(successSpy);
    expect(successSpy).toHaveBeenCalledTimes(1);
    expect(successSpy.calls.mostRecent().args[0]).toEqual('5 minutes ago');
  });

  it('should transform string-date 30 seconds ago (less than a minute)', () => {
    const delta = Date.now() - 30 * 1000;
    const result: Observable<string> = pipe.transform(new Date(delta).toString());
    const successSpy = jasmine.createSpy('successSpy');
    result.subscribe(successSpy);
    expect(successSpy).toHaveBeenCalledTimes(1);
    expect(successSpy.calls.mostRecent().args[0]).toEqual('less than a minute ago');
  });

  it('should transform string-date 1h hours ago', () => {
    const delta = Date.now() - 60 * 60 * 1000;
    const result: Observable<string> = pipe.transform(new Date(delta).toString());
    const successSpy = jasmine.createSpy('successSpy');
    result.subscribe(successSpy);
    expect(successSpy).toHaveBeenCalledTimes(1);
    expect(successSpy.calls.mostRecent().args[0]).toEqual('1 hours ago');
  });

  it('should transform string-date 2h hours ago', () => {
    const delta = Date.now() - 2 * 60 * 60 * 1000;
    const result: Observable<string> = pipe.transform(new Date(delta).toString());
    const successSpy = jasmine.createSpy('successSpy');
    result.subscribe(successSpy);
    expect(successSpy).toHaveBeenCalledTimes(1);
    expect(successSpy.calls.mostRecent().args[0]).toEqual('2 hours ago');
  });

  it('should transform string-date 1h and 20 minutes ago', () => {
    const delta = Date.now() - 80 * 60 * 1000;
    const result: Observable<string> = pipe.transform(new Date(delta).toString());
    const successSpy = jasmine.createSpy('successSpy');
    result.subscribe(successSpy);
    expect(successSpy).toHaveBeenCalledTimes(1);
    expect(successSpy.calls.mostRecent().args[0]).toEqual('1 hours and 20 minutes ago');
  });

  it('should transform string-date 2 days ago', () => {
    const delta = Date.now() - 2 * 24 * 60 * 60 * 1000;
    const result: Observable<string> = pipe.transform(new Date(delta).toString());
    const successSpy = jasmine.createSpy('successSpy');
    result.subscribe(successSpy);
    expect(successSpy).toHaveBeenCalledTimes(1);
    expect(successSpy.calls.mostRecent().args[0]).toEqual('2 days ago');
  });

  it('should transform string-date 1 weeks and 2 days ago', () => {
    const delta = Date.now() - 9 * 24 * 60 * 60 * 1000;
    const result: Observable<string> = pipe.transform(new Date(delta).toString());
    const successSpy = jasmine.createSpy('successSpy');
    result.subscribe(successSpy);
    expect(successSpy).toHaveBeenCalledTimes(1);
    expect(successSpy.calls.mostRecent().args[0]).toEqual('1 weeks and 2 days ago');
  });

  it('should transform string-date 2 weeks ago', () => {
    const delta = Date.now() - 14 * 24 * 60 * 60 * 1000;
    const result: Observable<string> = pipe.transform(new Date(delta).toString());
    const successSpy = jasmine.createSpy('successSpy');
    result.subscribe(successSpy);
    expect(successSpy).toHaveBeenCalledTimes(1);
    expect(successSpy.calls.mostRecent().args[0]).toEqual('2 weeks ago');
  });

  it('should transform string-date 1 months and 1 weeks ago', () => {
    const delta = Date.now() - 38 * 24 * 60 * 60 * 1000;
    const result: Observable<string> = pipe.transform(new Date(delta).toString());
    const successSpy = jasmine.createSpy('successSpy');
    result.subscribe(successSpy);
    expect(successSpy).toHaveBeenCalledTimes(1);
    expect(successSpy.calls.mostRecent().args[0]).toEqual('1 months and 1 weeks ago');
  });

  it('should transform string-date 2 months ago', () => {
    const delta = Date.now() - 60 * 24 * 60 * 60 * 1000;
    const result: Observable<string> = pipe.transform(new Date(delta).toString());
    const successSpy = jasmine.createSpy('successSpy');
    result.subscribe(successSpy);
    expect(successSpy).toHaveBeenCalledTimes(1);
    expect(successSpy.calls.mostRecent().args[0]).toEqual('2 months ago');
  });

  it('should transform string-date 1 years and 1 months ago', () => {
    const delta = Date.now() - (365 + 30) * 24 * 60 * 60 * 1000;
    const result: Observable<string> = pipe.transform(new Date(delta).toString());
    const successSpy = jasmine.createSpy('successSpy');
    result.subscribe(successSpy);
    expect(successSpy).toHaveBeenCalledTimes(1);
    expect(successSpy.calls.mostRecent().args[0]).toEqual('1 years and 1 months ago');
  });

  it('should transform string-date 1 years and 1 months ago', () => {
    const delta = Date.now() - 2 * 365 * 24 * 60 * 60 * 1000;
    const result: Observable<string> = pipe.transform(new Date(delta).toString());
    const successSpy = jasmine.createSpy('successSpy');
    result.subscribe(successSpy);
    expect(successSpy).toHaveBeenCalledTimes(1);
    expect(successSpy.calls.mostRecent().args[0]).toEqual('2 years ago');
  });
});

describe('KeysPipe', () => {
  let pipe: KeysPipe;

  beforeEach(() => {
    pipe = new KeysPipe();
  });

  it('should transform keys', () => {
    const object = { a: '1', b: '2', c: 3, foo: 'foo' };
    expect(pipe.transform(object)).toEqual(['a', 'b', 'c', 'foo']);
  });
});
