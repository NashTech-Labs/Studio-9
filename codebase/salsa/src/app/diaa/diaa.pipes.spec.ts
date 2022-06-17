import { DecileRangePipe } from './diaa.pipes';

describe('DecileRangePipe', () => {
  let pipe: DecileRangePipe;

  beforeEach(() => {
    pipe = new DecileRangePipe();
  });

  it('should return a value', () => {
    const [min, max] = [0, 1];
    expect(pipe.transform([min, max])).toEqual(`${(min - 1) * 10}% to ${max * 10}%`);
  });
});
