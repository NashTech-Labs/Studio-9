import * as d3 from 'd3';
import * as jsc from 'jsverify';

import { D3ChartHelper, IAxisParameters } from './d3-chart.helper';

describe('D3ChartHelper', () => {
  let helper: D3ChartHelper;
  let el: SVGElement;

  beforeEach(() => {
    el = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    el.style.width = '400px';
    el.style.height = '300px';

    const body: HTMLBodyElement = document.getElementsByTagName('body')[0];

    body.appendChild(el);

    helper = new D3ChartHelper(el);
  });

  it('should add g.chartArea', () => {
    expect(el.querySelectorAll('g.chartArea').length).toBe(1);
  });

  it('should update chart area position on margin change', () => {
    helper.margin = {
      left: 10,
      top: 10,
      right: 10,
      bottom: 10,
    };

    expect(helper.chartArea.attr('transform')).toMatch(/^translate\(\d+, 10\)/);
  });

  it('should return SVG size', () => {
    expect(helper.size).toEqual({
      width: 400,
      height: 300,
    });
  });

  it('should return chart size with margins excluded', () => {
    helper.margin = {
      left: 10,
      top: 20,
      right: 30,
      bottom: 40,
    };

    expect(helper.chartSize).toEqual({
      width: 360,
      height: 240,
    });
  });

  it('should have axis ranges defined by chart size', () => {
    helper.margin = {
      left: Math.random() * 10,
      top: Math.random() * 10,
      right: Math.random() * 10,
      bottom: Math.random() * 10,
    };

    const chartSize = helper.chartSize;

    expect(helper.xRange).toEqual([0, chartSize.width]);
    expect(helper.yRange).toEqual([chartSize.height, 0]);
  });

  it('should create legend holder element if needed and exactly once', () => {
    helper.legend;
    expect(el.querySelectorAll('g.legend').length).toBe(1);

    helper.legend;
    expect(el.querySelectorAll('g.legend').length).toBe(1);
  });

  it('should add title element if requested and keep it exactly one per chart', () => {
    helper.appendTitle('Title', 'my-title');
    expect(el.querySelectorAll('text.my-title').length).toBe(1);

    helper.appendTitle('Title', 'my-title');
    expect(el.querySelectorAll('text.my-title').length).toBe(1);
  });

  it('should add axis elements and keep it exactly one of a kind per chart', () => {
    const domain = jsc.suchthat(jsc.nearray(jsc.integer), _ => _.length > 1);
    const axis = jsc.record<IAxisParameters<number>>({
      //location: jsc.constant<D3ChartHelper.AssetLocation>(<any> 'foo'),
      location: jsc.elements<D3ChartHelper.AssetLocation>(<any> Object.keys(D3ChartHelper.AssetLocation)),
      label: jsc.nestring,
      scale: domain.smap<d3.ScaleBand<number>>(
        _ => d3.scaleBand<number>()
          .domain(_)
          .rangeRound(helper.xRange),
        scale => scale.domain(),
      ),
      options: jsc.oneof<D3ChartHelper.AxisOptions<number>>([
        jsc.constant(undefined),
        jsc.record({
          ticks: jsc.oneof([jsc.constant<number[]>(undefined), domain]),
          tickFormat: jsc.constant((value: number, i: number) => String(value)),
          angle: jsc.elements([0, 90]),
        }),
      ]),
    });

    jsc.assertForall(axis, _ => {
      helper.setAxes(_);
      return el.querySelectorAll('g.axis-' + _.location).length === 1;
    });
  });
});
