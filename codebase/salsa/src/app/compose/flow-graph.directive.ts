import { Directive, ElementRef, Input, OnChanges, OnDestroy } from '@angular/core';

import * as dagreD3 from 'dagre-d3-renderer';

import config from '../config';
import { IObjectHash } from '../core/interfaces/common.interface';
import { ITable } from '../tables/table.interface';

import { IFlow, IFlowstep } from './flow.interface';

// @todo Improving: set some additional info about step transformation options on output edge (from Operation)
// @todo Improving: maybe we want center the graph with scaling (not constant scale param)
// @todo Improving: set boundaries for dragging layout
// @todo Improving: tooltips on hover
// http://cpettitt.github.io/project/dagre-d3/latest/demo/hover.html
// @todo Research: is it correct destroy implementation?

interface IFlowGraphNodeOptions {
  shape: string;
  'class': string;
  rx?: number;
  ry?: number;
}

interface IFlowGraphOptions {
  maxScale: number;
  templates: {
    inputNode: IFlowGraphNodeOptions;
    outputNode: IFlowGraphNodeOptions;
    stepNode: IFlowGraphNodeOptions;
    inputEdge: { arrowhead: string },
    outputEdge: { arrowhead: string }
  };
}

interface IFlowGraphNode extends IFlowGraphNodeOptions {
  elem: HTMLElement;
}

/**
 * Usage:
 *  <svg flow-graph [flow]="IFlow" [tables]="ITable[]" [options]="any"></svg>
 */
@Directive({
  selector: '[flow-graph]',
})
export class FlowGraphDirective implements OnChanges, OnDestroy {
  @Input() flow: IFlow = null;
  @Input() tables: ITable[] = null;
  @Input() options: IFlowGraphOptions = null;
  @Input() container: string = null;
  config = config;
  private el: HTMLElement;
  private graph: any; // @todo Saved graph into field for lazy rendering (if we want some collapsible logic)

  constructor(el: ElementRef) {
    this.el = el.nativeElement;
  }

  ngOnChanges() {
    // for rendering: we should have non-empty steps list, tables list and some step should has DONE status
    if (this.flow && this.flow.steps.length > 0 &&
      this.tables && this.tables.length > 0 &&
      this.flow.steps.some(step => step.status === config.flowstep.status.values.DONE)
    ) {
      this.options = Object.assign({}, config.graph.flow, this.options);
      this.init() && this.render();
    }
  }

  ngOnDestroy() {
    this.destroy();
  }

  private init(): boolean {
    if (!dagreD3) return false;
    const g = new dagreD3.graphlib.Graph().setGraph({});
    const tablesHash = this.tables.reduce<IObjectHash<ITable>>((acc, table) => {
      acc[table.id] = table;
      return acc;
    }, {});

    // Traverse Steps
    this.flow.steps.forEach((step: IFlowstep) => {
      if (step.status === config.flowstep.status.values.DONE) {
        // Add step node
        const stepId = step.id;

        g.setNode(stepId,
          Object.assign({}, this.options.templates.stepNode, {
            label: config.flowstep.type.labels[step.type],
            // label: `${step.name} (${config.flowstep.type.labels[step.type]})`,
          }));

        // Add tables nodes (with relations)
        step.input.forEach((inputId: string) => {
          const inputTable = tablesHash[inputId];
          g.setNode(inputId, Object.assign({}, this.options.templates.inputNode, {
            label: inputTable ? inputTable.name : inputId,
          }));
          g.setEdge(inputId, stepId, Object.assign({}, this.options.templates.inputEdge));
        });

        const outputId = step.output;
        const outputTable = tablesHash[outputId];

        g.setNode(outputId, Object.assign({}, this.options.templates.outputNode, {
          label: outputTable ? outputTable.name : outputId,
        }));

        g.setEdge(stepId, outputId, Object.assign({}, this.options.templates.outputEdge));
      }
    });

    this.graph = g;
    return true;
  }

  private beautify(step: IFlowstep): string {
    let html = `<table class='graph-info'>`;
    switch (step.type) {
      case IFlowstep.Type.query:
        const queryTransformer = <IFlowstep.QueryTransformer> step.options;
        html = `<tr><td>Query:</td><td>${queryTransformer.expression}</td></tr>`;
        break;
      case IFlowstep.Type.insert:
        const insertOptions = <IFlowstep.InsertTransformer> step.options;
        html += `<tr><td>Column Name:</td><td>${insertOptions.name}</td></tr>`;
        html += `<tr><td>Formula:</td><td>${insertOptions.formula}</td></tr>`;
        html += this.stepPassColumns(step);
        break;
      case IFlowstep.Type.cluster:
        const clusterOptions = <IFlowstep.ClusterTransformer> step.options;
        html += `<tr><td>type:</td><td>${clusterOptions.type}</td></tr>`;
        html += `<tr><td>Groups:</td><td>${clusterOptions.groups}</td></tr>`;
        html += `<tr><td>Iterations:</td><td>${clusterOptions.iterations}</td></tr>`;
        html += this.beautifyList(step, 'Columns', clusterOptions.columns);
        html += this.stepPassColumns(step);
        break;
      case IFlowstep.Type.aggregate:
        const aggregateOptions = <IFlowstep.AggregateTransformer> step.options;
        html += `<tr><td>Operand Column:</td><td>${aggregateOptions.operandColumn}</td></tr>`;
        html += `<tr><td>Operator:</td><td>${aggregateOptions.operator}</td></tr>`;
        html += `<tr><td>New Column Name:</td><td>${aggregateOptions.name}</td></tr>`;
        html += this.beautifyList(step, 'Group By Columns', aggregateOptions.groupByColumns);
        break;
      case IFlowstep.Type.filter:
        const filterOptions = <IFlowstep.FilterTransformer> step.options;
        html += this.beautifyList(step, 'Filters', filterOptions.conditions,
          ['Column', 'Relational Operator', 'Value', 'Logical Operator'],
          ['columnName', (item: IFlowstep.FilterCondition) => {
            return config.flowstep.option.filter.operator.labels[item.operator];
          }, 'value', (item: IFlowstep.FilterCondition, i) => {
            if (i !== filterOptions.conditions.length - 1) {
              return item.operatorGroup;
            } else {
              return '';
            }
          }]);
        html += this.stepPassColumns(step);
        break;
      case IFlowstep.Type.join:
        const joinOptions = <IFlowstep.JoinTransformer> step.options;
        let type = '';
        switch (joinOptions.type) {
          case IFlowstep.JoinType.NORMAL:
            type = 'Exact Match';
            break;
          case IFlowstep.JoinType.FUZZY:
            type = 'Fuzzy Match';
            break;
          default:
            type = 'Unexpected Join Type';
        }
        html += `<tr><td>Left Join Type</td><td>${type}</td></tr>`;
        html += `<tr><td>Left Prefix</td><td>${joinOptions.leftPrefix}</td></tr>`;
        html += `<tr><td>Right Prefix</td><td>${joinOptions.rightPrefix}</td></tr>`;
        html += this.beautifyList(step, 'Joined Columns', joinOptions.columns, ['From', 'To'], ['from', 'to']);
        html += this.stepPassColumns(step);
        break;
      case IFlowstep.Type.window:
        const windowOptions = <IFlowstep.WindowTransformer> step.options;
        html += `<tr>`;
        html += `<td>Aggregator:</td><td>${windowOptions.aggregator}</td>`;
        html += `</tr>`;
        if (windowOptions.aggregatorArg) {
          html += `<tr>`;
          html += `<td>Aggregator Arg:</td><td>${windowOptions.aggregatorArg}</td>`;
          html += `</tr>`;
        }
        html += `<tr>`;
        html += `<td>New Column Name:</td><td>${windowOptions.newColName}</td>`;
        html += `</tr>`;
        if (windowOptions.partitionBy) {
          html += this.beautifyList(step, 'Partition By', windowOptions.partitionBy);
        }
        if (windowOptions.orderBy) {
          html += this.beautifyList(step, 'Order By', windowOptions.orderBy);
        }
        if (windowOptions.orderBy || windowOptions.withinGroupExpression) {
          html += `<tr>`;
          html += `<td>isDesc:</td><td>${windowOptions.isDesc}</td>`;
          html += `</tr>`;
        }
        if (windowOptions.withinGroupExpression) {
          html += this.beautifyList(step, 'Within Group', windowOptions.withinGroupExpression);
        }
        if (windowOptions.respectNulls) {
          html += `<tr>`;
          html += `<td>Respect Nulls:</td><td>${windowOptions.respectNulls}</td>`;
          html += `</tr>`;
        }
        if (windowOptions.ignoreNulls) {
          html += `<tr>`;
          html += `<td>Ignore Nulls:</td><td>${windowOptions.ignoreNulls}</td>`;
          html += `</tr>`;
        }
        if (windowOptions.listaggDelimiter) {
          html += `<tr>`;
          html += `<td>Delimiter:</td><td>${windowOptions.listaggDelimiter}</td>`;
          html += `</tr>`;
        }
        if (windowOptions.ntileGroupsCount) {
          html += `<tr>`;
          html += `<td>Groups count:</td><td>${windowOptions.ntileGroupsCount}</td>`;
          html += `</tr>`;
        }
        if (windowOptions.percentile) {
          html += `<tr>`;
          html += `<td>Percentile Value:</td><td>${windowOptions.percentile}</td>`;
          html += `</tr>`;
        }
        html += this.stepPassColumns(step);
        break;
      case IFlowstep.Type.geojoin:
        const geoJoinOptions = <IFlowstep.GeoJoinTransformer> step.options;
        html += `<tr><td>Left Prefix</td><td>${geoJoinOptions.leftPrefix}</td></tr>`;
        html += `<tr><td>Right Prefix</td><td>${geoJoinOptions.rightPrefix}</td></tr>`;
        html += this.beautifyList(step, 'Join Conditions', geoJoinOptions.joinConditions,
          [''],
          [(item: IFlowstep.GeoJoinCondition) => {
            let innerHtml = '';
            const configGeo = config.flowstep.option.geojoin;
            const geometryA = FlowGraphDirective._geometrySummary(item.left);
            const geometryB = FlowGraphDirective._geometrySummary(item.right);
            const relationArguments = item.relation.relationBase === configGeo.relationBase.values.LEFT
              ? `(${geometryA}, ${geometryB})`
              : `(${geometryB}, ${geometryA})`;
            let summary = `${configGeo.relation.labels[item.relation.relType]} ${relationArguments}`;
            if (FlowGraphDirective.relationHasParameter(item.relation.relType)) {
              summary += ` ${configGeo.operator.labels[item.relation.operator]} ${item.relation.value}`;
            }
            innerHtml += `<tr><td colspan="2">${summary}</td></tr>`;
            return innerHtml;
          }]);
        html += this.stepPassColumns(step);
        break;
      case IFlowstep.Type.map:
        const mapOptions = <IFlowstep.MapTransformer> step.options;
        html += `<tr><td>Accept Only Renamed</td><td>${mapOptions.onlyRenamed}</td></tr>`;
        html += this.beautifyList(step, 'Renamings', mapOptions.changes, ['Origin Name', 'New Name'], ['value', 'name']);
        break;
      default:
        html += `<tr>`;
        html += `<td>Unexpected Transformer Type</td>`;
        html += `</tr>`;
    }
    html += '</table>';
    return html;
  }

  private stepPassColumns(step) {
    if (step.options.passColumns && step.options.passColumns.length) {
      return this.beautifyList(step, 'Selected Columns', step.options.passColumns,
        ['Column Name', 'Alias', 'Table'],
        ['columnName', 'newColumnName', (item) => {
          let table = this.tables.find((table: ITable) => table.id === step.input[item.tableReference]);
          return table ? table.name : step.input[item.tableReference];
        }]);
    }
    return '';
  }

  private tryGetDisplayName(step, columnName: string, i: number) {
    const table = this.tables.find(_ => _.id === step.input[i]);
    if (!table) {
      return columnName;
    }
    const column = table.columns.find(_ => _.name === columnName);
    if (!column) {
      return columnName;
    }
    return `${column.displayName}`;
  }

  private beautifyList(step: IFlowstep, listName: string, array: any[], labels?: string[], properties?: any[]): string {
    let html = '';
    (array || []).forEach((column: any, i: number) => {
      if (i === 0) {
        html += '<tr>';
        html += `<td>${listName}:</td>`;
        if (labels) {
          labels.forEach((label: string) => {
            html += `<td>${label}</td>`;
          });
        }
        html += '</tr>';
      }
      html += '<tr>';
      html += '<td></td>';
      if (!properties) {
        html += `<td>${this.tryGetDisplayName(step, column, 0)}</td>`;
      } else {
        properties.forEach((property: (string | Function), j: number) => {
          if (typeof property === 'function') {
            html += `<td>${this.tryGetDisplayName(step, property(column, j), j)}</td>`;
          } else {
            html += `<td>${this.tryGetDisplayName(step, column[property], j)}</td>`;
          }
        });
      }
      html += '</tr>';
    });
    return html;
  }

  private render() {
    this.destroy();

    let dagreRender: Function = new dagreD3.render(),
      svg: any = dagreD3.d3.select(this.el).attr('class', 'rendered'),
      inner = svg.append('g');

    dagreRender(inner, this.graph);
    const padding = { vertical: 0, horizontal: 40 };
    svg.attr('viewBox', `0 0 ${this.graph.graph().width + padding.horizontal} ${this.graph.graph().height + padding.vertical}`);
    svg.attr('width', (this.graph.graph().width + padding.horizontal) * this.options.maxScale);
    // append tooltips
    inner.selectAll('g.node.operation-template')
      .attr('title', (v) => {
        let currentShape: IFlowGraphNode = this.graph.node(v);
        let step = this.flow.steps.find((step) => step.id === v);
        $(currentShape.elem).attr('data-html', 'true');
        $(currentShape.elem).attr('data-placement', 'bottom');
        $(currentShape.elem).attr('data-original-title', this.beautify(step));
        $(currentShape.elem).attr('data-container', this.container ? this.container : 'body');
        (<any> $(currentShape.elem)).tooltip();
      });
  }

  private destroy() {
    if (!dagreD3) return;

    let svg = dagreD3.d3.select(this.el),
      inner = svg.select('g');

    svg.classed('rendered', false);
    if (inner) inner.remove();
  }

  static _geometrySummary(value: IFlowstep.GeoSpatialGeometryComposition) {
    const args = value.coordinates.map(point => {
      return `${point.lat}, ${point.lon}`;
    }).join(', ');
    return `${config.flowstep.option.geojoin.geometry.labels[value.geoType]}(${args})`;
  }

  static relationHasParameter(relationType: string): boolean {
    return config.flowstep.option.geojoin.relationsWithParameter.indexOf(relationType) >= 0;
  }
}
