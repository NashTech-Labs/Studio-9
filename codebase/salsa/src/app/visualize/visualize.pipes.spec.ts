import { AttributesListPipe, MetricsListPipe } from './chart-edit-context.component';
import { IDashboard } from './dashboard.interface';

describe('MetricsListPipe', () => {
  let pipe: MetricsListPipe;

  beforeEach(() => {
    pipe = new MetricsListPipe();
  });

  it('should return a value', () => {
    expect(pipe.transform([
        { name: 'name0', displayName: 'displayName0' },
        { name: 'name1', displayName: 'displayName1' },
        { name: 'name2', displayName: 'displayName2' },
      ],
      [
        { columnName: 'name0', aggregator: IDashboard.DashboardAggregationType.SUM },
        { columnName: 'name1', aggregator: IDashboard.DashboardAggregationType.COUNT },
      ],
    )).toEqual([
      { name: 'name0', displayName: 'displayName0', type: IDashboard.DashboardAggregationType.SUM },
      { name: 'name1', displayName: 'displayName1', type: IDashboard.DashboardAggregationType.COUNT },
      { name: 'name2', displayName: 'displayName2', type: null },
    ]);
  });
});


describe('AttributesListPipe', () => {
  let pipe: AttributesListPipe;

  beforeEach(() => {
    pipe = new AttributesListPipe();
  });

  it('should return a value', () => {
    expect(pipe.transform([
        { name: 'name0', displayName: 'displayName0' },
        { name: 'name1', displayName: 'displayName1' },
        { name: 'name2', displayName: 'displayName2' },
      ],
      ['name0', 'name2'],
    )).toEqual([
      { name: 'name0', displayName: 'displayName0', isActive: true },
      { name: 'name1', displayName: 'displayName1', isActive: false },
      { name: 'name2', displayName: 'displayName2', isActive: true },
    ]);
  });
});
