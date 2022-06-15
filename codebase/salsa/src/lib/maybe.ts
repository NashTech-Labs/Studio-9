export interface Maybe<T> {
  readonly isDefined: boolean;

  get(): T | null;

  map<R>(f: (wrapped: T) => R): Maybe<R>;

  flatMap<R>(f: (wrapped: T) => Maybe<R>): Maybe<R>;

  filter(f: (wrapped: T) => boolean): Maybe<T>;
}

//noinspection JSUnusedLocalSymbols
export const None: Maybe<any> = {
  isDefined: false,
  get: function() {
    return null;
  },
  map: function<R>(f: (wrapped: any) => R): Maybe<R> {
    return None;
  },
  flatMap: function<R>(f: (wrapped: any) => Maybe<R>): Maybe<R> {
    return None;
  },
  filter: function(f: <T>(wrapped: T) => boolean): Maybe<any> {
    return None;
  },
};

export class Some<T> implements Maybe<T> {
  get isDefined() {
    return true;
  }

  constructor(private value: T) {}

  get(): T {
    return this.value;
  }

  map<R>(f: (wrapped: T) => R): Maybe<R> {
    return maybe(f(this.value));
  }

  flatMap<R>(f: (wrapped: T) => Maybe<R>): Maybe<R> {
    return f(this.value);
  }

  filter(f: (wrapped: T) => boolean): Maybe<T> {
    return f(this.value) ? this : None;
  }
}

export function maybe<T>(value: T): Maybe<T> {
  return value !== undefined && value !== null ? new Some(value) : None;
}

export namespace maybe {
  export function seq<T>(s: Maybe<T>[]): T[] {
    return s.filter(_ => _.isDefined).map(_ => _.get());
  }
}
