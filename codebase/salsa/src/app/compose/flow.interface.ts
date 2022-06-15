import { IAsset, TObjectId } from '../core/interfaces/common.interface';
import { ITableColumn } from '../tables/table.interface';

export interface IFlowCreate {
  name: string;
  description?: string;
}

export interface IFlowUpdate {
  id?: TObjectId;
  name?: string;
  description?: string;
}

export interface IFlow extends IAsset {
  description: string;
  status: IFlow.Status;
  steps: IFlowstep[];
  tables: TObjectId[];
  inLibrary?: boolean;
}

export namespace IFlow {
  export enum Status {
    NEW = 'NEW',
    RUNNING = 'RUNNING',
    ERROR = 'ERROR',
    PARTLY_DONE = 'PARTLY_DONE',
    DONE = 'DONE',
    DELETED = 'DELETED',
    QUEUED = 'QUEUED',
  }
}

export interface IFlowInput {
  tableId: TObjectId;
  tableName: string;
  columns: ITableColumn[];
}

export interface IFlowOutput {
  tableId: TObjectId;
  tableName: string;
}

export interface IFlowClone {
  name?: string;
}

export interface IClusteringResult {
  centers: number[][];
  points: number[][];
}

export interface IFlowstep {
  id: TObjectId;
  name: string;
  type: IFlowstep.Type;
  created: string;
  updated: string;
  input: TObjectId[];
  output: TObjectId;
  status: IFlowstep.Status;
  options: IFlowstep.Transformer;
}

export namespace IFlowstep {
  export interface Create {
    type: IFlowstep.Type;
    name: string;
    input: TObjectId[];
    options: Transformer;
    output: string;
  }

  export interface Update {
    name: string;
  }

  export type Transformer = InsertTransformer
    | ClusterTransformer
    | QueryTransformer
    | AggregateTransformer
    | FilterTransformer
    | JoinTransformer
    | GeoJoinTransformer
    | MapTransformer
    | WindowTransformer;

  export interface GeoSpatialGeometryComposition {
    geoType: IFlowstep.GeoGeometryType;
    coordinates: {
      lat: string;
      lon: string;
    }[];
  }

  export interface IPassColumn {
    columnName: string;
    tableReference: string;
    newColumnName: string;
  }

  export interface QueryTransformer {
    expression: string;
    inputAliases: string[];
  }

  export interface InsertTransformer {
    name: string;
    formula: string;
    passColumns?: IPassColumn[];
  }

  export interface ClusterTransformer {
    type: ClusterType;
    groups: string;
    iterations: string;
    columns: string[];
    passColumns?: IPassColumn[];
  }

  export interface AggregateTransformer {
    name: string;
    operator: string;
    operandColumn: string;
    groupByColumns: string[];
  }

  export interface FilterCondition {
    column: string;
    operator: FilterRelationalOperator;
    value: string | number;
    operatorGroup: FilterLogicalOperator;
  }

  export interface FilterTransformer {
    conditions: FilterCondition[];
    passColumns?: IPassColumn[];
  }

  export interface JoinCondition {
    from: string;
    to: string;
  }

  export interface JoinTransformer {
    type: JoinType;
    leftPrefix: string;
    rightPrefix: string;
    columns: JoinCondition[];
    passColumns?: IPassColumn[];
  }

  export interface WindowTransformer {
    aggregator: string;
    newColName: string;
    aggregatorArg?: string;
    percentile?: number | string;
    orderBy?: string[];
    partitionBy?: string[];
    windowLowerBound?: number | string;
    windowUpperBound?: number | string;
    isDesc?: boolean;
    withinGroupExpression?: string[];
    listaggDelimiter?: string;
    ntileGroupsCount?: number | string;
    offset?: number | string;
    ignoreNulls?: boolean;
    respectNulls?: boolean;
    passColumns?: IPassColumn[];
  }

  export interface GeoRelation {
    relType: GeoRelationType;
    operator: GeoRelationalOperator;
    value: string;
    relationBase: GeoRelationBase;
  }

  export interface GeoJoinCondition {
    left: GeoSpatialGeometryComposition;
    right: GeoSpatialGeometryComposition;
    relation: GeoRelation;
  }

  export interface GeoJoinTransformer {
    leftPrefix: string;
    rightPrefix: string;
    joinConditions: GeoJoinCondition[];
    passColumns?: IPassColumn[];
  }

  export interface MapChange {
    name: string;
    value: string;
  }

  export interface MapTransformer {
    changes: MapChange[];
    onlyRenamed: boolean;
  }

  export enum GeoRelationBase {
    LEFT= 'LEFT',
    RIGHT= 'RIGHT',
  }

  export enum GeoGeometryType {
    POINT = 'POINT',
    POLYGON = 'POLYGON',
  }

  export enum GeoRelationType {
    ST_DISTANCE = 'ST_DISTANCE',
    ST_CONTAINS = 'ST_CONTAINS',
  }

  export enum JoinType {
    NORMAL = 'NORMAL',
    FUZZY = 'FUZZY',
  }

  export enum ClusterType {
    K_MEANS = 'K_MEANS',
    FUZZY_C_MEANS = 'FUZZY_C_MEANS',
  }

  export enum Status {
    NEW = 'NEW',
    RUNNING = 'RUNNING',
    ERROR = 'ERROR',
    PARTLY_DONE = 'PARTLY_DONE',
    DONE = 'DONE',
    DELETED = 'DELETED',
    QUEUED = 'QUEUED',
  }

  export enum Type {
    insert = 'insert',
    aggregate = 'aggregate',
    join = 'join',
    cluster = 'cluster',
    query = 'query',
    window = 'window',
    filter = 'filter',
    map = 'map',
    geojoin = 'geojoin',
  }

  export enum FilterRelationalOperator {
    EQ = 'EQ',
    NE = 'NE',
    LT = 'LT',
    GT = 'GT',
  }

  export enum GeoRelationalOperator {
    EQ = 'EQ',
    NE = 'NE',
    LT = 'LT',
    GT = 'GT',
    LTE = 'LTE',
    GTE = 'GTE',
  }

  export enum FilterLogicalOperator {
    AND = 'AND',
    OR = 'OR',
  }
}





// BACKEND
export interface IBackendFlow extends IAsset {
  status: IFlow.Status;
  steps: IBackendFlowstep[];
  tables: TObjectId[];
  inLibrary: boolean;
}

export interface IBackendFlowUpdate {
  name?: string;
  description?: string;
}

export interface IBackendFlowstep {
  id: TObjectId;
  name: string;
  created?: string; // TODO DO NOT EXISTS
  updated: string;
  input: TObjectId[];
  output: TObjectId;
  status: IFlowstep.Status;
  transformer: IBackendFlowstep.Transformer;
}

export namespace IBackendFlowstep {
  export interface Create {
    name: string;
    inputIDs: TObjectId[];
    transformer: Transformer;
    outputName: string;
  }

  export interface Update {
    name: string;
  }

  export enum Type {
    INSERT = 'InsertColumn',
    AGGREGATE = 'Aggregate',
    JOIN = 'Join',
    FUZZYJOIN = 'FuzzyJoin',
    CLUSTER = 'Cluster',
    QUERY = 'SqlTransformer',
    WINDOW = 'WindowFunctionTransformer',
    FILTER = 'Filter',
    MAP = 'RenameColumns',
    GEOJOIN = 'GeoSpatialJoin',
  }

  export type Transformer = InsertTransformer
    | ClusterTransformer
    | AggregateTransformer
    | FilterTransformer
    | JoinTransformer
    | FuzzyJoinTransformer
    | MapTransformer
    | GeoJoinTransformer
    | WindowTransformer
    | QueryTransformer;

  export interface InsertTransformer {
    transformerType: Type.INSERT;
    expression: string;
    newColumnName: string;
    passColumns?: IFlowstep.IPassColumn[];
  }

  export interface ClusterTransformer {
    transformerType: Type.CLUSTER;
    clusteringType: IFlowstep.ClusterType;
    columns: string[];
    numClusters: number;
    numIterations: number;
    passColumns?: IFlowstep.IPassColumn[];
  }

  export interface AggregateTransformer {
    transformerType: Type.AGGREGATE;
    aggInfo: {
      aggregateColumn: string;
      groupByColumns: string[];
      operandColumn: string;
      operator: string;
    };
  }

  export interface MapTransformer extends IFlowstep.MapTransformer {
    transformerType: Type.MAP;
  }

  export interface WindowTransformer extends IFlowstep.WindowTransformer {
    transformerType: Type.WINDOW;
  }

  export interface QueryTransformer extends IFlowstep.QueryTransformer {
    transformerType: Type.QUERY;
  }

  export interface GeoJoinTransformer extends IFlowstep.GeoJoinTransformer {
    transformerType: Type.GEOJOIN;
  }

  export interface FilterTransformer {
    transformerType: Type.FILTER;
    filters: {
      columnName: string;
      value: string;
      relationalOperator: IFlowstep.FilterRelationalOperator;
      logicalOperator: IFlowstep.FilterLogicalOperator
    } [];
  }

  export interface JoinCondition {
    leftColumnName: string;
    rightColumnName: string;
  }

  export interface JoinTransformer {
    transformerType: Type.JOIN;
    joinColumns: JoinCondition[];
    leftPrefix: string;
    rightPrefix: string;
    passColumns?: IFlowstep.IPassColumn[];
  }

  export interface FuzzyJoinTransformer {
    transformerType: Type.FUZZYJOIN;
    joinColumns: JoinCondition[];
    leftPrefix: string;
    rightPrefix: string;
    passColumns?: IFlowstep.IPassColumn[];
  }
}

