import { async } from '@angular/core/testing';

import { Observable } from 'rxjs/Observable';
import { Subject } from 'rxjs/Subject';

import { ReactiveLoader } from './reactive-loader';

describe('ReactiveLoader', () => {

  it('should have loaded false at creation', () => {
    const loader = new ReactiveLoader(() => {
      fail('Loading function is not expected to be called');
      return Observable.empty();
    });

    expect(loader.loaded).toBe(false);
  });

  it('should have loaded true at creation if explicitly asked', () => {
    const loader = new ReactiveLoader(
      () => {
        fail('Loading function is not expected to be called');
        return Observable.empty();
      },
      true,
    );

    expect(loader.loaded).toBe(true);
  });

  it('should have loaded true after successful completion', async(() => {
    const loader = new ReactiveLoader(() => Observable.of(1));
    loader.subscribe(() => {
      expect(loader.loaded).toBe(true);
    });

    loader.load(null);
  }));

  it('should have loaded false for empty observable', async(() => {
    const loader = new ReactiveLoader(() => Observable.empty());
    loader.subscribe(() => {
      expect(loader.loaded).toBe(false);
    });

    loader.load(null);
  }));

  it('should have loaded false for failure observable', async(() => {
    const loader = new ReactiveLoader(() => Observable.empty());
    loader.subscribe(() => fail('No result is expected'));

    loader.load(null);
    expect(loader.loaded).toBe(false);
  }));



  it('should be not active from beginning', async(() => {
    const loader = new ReactiveLoader(() => Observable.of(1));

    loader.active.first().subscribe(active => {
      expect(active).toBe(false);
    });
  }));

  it('should be active while observable is not complete', async(() => {
    const o = new Subject();
    const loader = new ReactiveLoader(() => o);
    loader.subscribe(() => fail('No result is expected'));

    loader.load(null);

    loader.active.first().subscribe(active => {
      expect(active).toBe(true);
      o.complete();
    });
  }));

  it('should not be active once observable emits an item', async(() => {
    const o = new Subject();
    const loader = new ReactiveLoader(() => o);
    loader.subscribe(() => o);

    loader.load(null);
    o.next(1);

    loader.active.first().subscribe(active => {
      expect(active).toBe(false);
      o.complete();
    });
  }));

  it('should not be active once observable complete', async(() => {
    const loader = new ReactiveLoader(() => Observable.empty());
    loader.subscribe(() => fail('No result is expected'));

    loader.load(null);

    loader.active.first().subscribe(active => {
      expect(active).toBe(false);
    });
  }));

  it('should not be active once observable errors', async(() => {
    const loader = new ReactiveLoader(() => Observable.throw('foo'));
    loader.subscribe(() => fail('No result is expected'));

    loader.load(null);

    loader.active.first().subscribe(active => {
      expect(active).toBe(false);
    });
  }));

  it('should remain active if second load is active but first completes', async(() => {
    const o1 = new Subject();
    const o2 = new Subject();
    const stream = [o1, o2];
    const loader = new ReactiveLoader(() => stream.shift());
    loader.subscribe(() => fail('No result is expected'));

    // first load
    loader.load(null);
    // second load
    loader.load(null);
    // first completes
    o1.complete();

    loader.active.first().subscribe(active => {
      expect(active).toBe(true);
      o2.complete();
    });
  }));


  it('should produce most recent requested value', async(() => {
    const o1 = new Subject();
    const o2 = new Subject();
    const stream = [o1, o2];
    const loader = new ReactiveLoader(() => stream.shift());
    loader.subscribe(v => {
      expect(v).toBe(2);
    });

    // first load
    loader.load(null);
    // second load
    loader.load(null);
    // first completes
    o1.next(1);
    o1.complete();
    // second completes
    o1.next(2);
    o1.complete();
  }));

  it('should produce two values if first request completes before second call', async(() => {
    const o1 = new Subject();
    const o2 = new Subject();
    const stream = [o1, o2];
    const loader = new ReactiveLoader(() => stream.shift());
    loader.value.take(2).toArray().subscribe(v => {
      expect(v).toBe([1, 2]);
    });

    // first load
    loader.load(null);
    // first completes
    o1.next(1);
    o1.complete();

    // second load
    loader.load(null);
    // second completes
    o1.next(2);
    o1.complete();
  }));
});
