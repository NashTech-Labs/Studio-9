import { IAlbum } from './albums/album.interface';
import { IFlow, IFlowstep } from './compose/flow.interface';
import { IAsset } from './core/interfaces/common.interface';
import { Feature } from './core/interfaces/feature-toggle.interface';
import { IProcess } from './core/interfaces/process.interface';
import { BinaryDataset } from './datasets/dataset.interfaces';
import { IS9Project, IS9ProjectSession } from './develop/s9-project.interfaces';
import { CVTLModelPrimitiveType } from './develop/package.interfaces';
import { IExperiment } from './experiments/experiment.interfaces';
import { IOptimization } from './optimize/optimization.interface';
import { ICVPredictionStatus } from './play/cv-prediction.interface';
import { IPredictionStatus } from './play/prediction.interface';
import { ITable } from './tables/table.interface';
import { ICVModel } from './train/cv-model.interface';
import { ITabularModel } from './train/model.interface';
import { UserRole, UserStatus } from './users/user.interfaces';
import { describeEnum } from './utils/misc';
import { DashboardCharts } from './visualize/charts/chart.interfaces';
import { IDashboard } from './visualize/dashboard.interface';

const hostname = window.location.hostname;

const api = {
  // 'localhost': 'http://vm-ab-req20161017.z1.netpoint-dc.com/api/insilico/v1.4/',
  'cor-771.exp.studio9.ai': 'http://develop.exp.studio9.ai/api/insilico/v1.4/',
  '192.168.1.234': 'http://192.168.1.144:9000/api/insilico/v1.4/',
  'localhost': 'http://studio9-dev-baile-elb-162323589.us-east-1.elb.amazonaws.com/baile/v2.0/',
  // Demo:
  'demo.dev.studio9.ai.s3-website-us-east-1.amazonaws.com': 'http://studio9-dev-baile-elb-162323589.us-east-1.elb.amazonaws.com/baile/v2.0/',
  //'localhost': 'http://localhost:9000/v2.0/',
  'dev.studio9.ai': 'https://dev.studio9.ai/api/insilico/v1.4/',
  'staging.studio9.ai': 'https://staging.studio9.ai/api/insilico/v1.4/',
  'insilico-test.knoldus.com': 'https://insilico-test.knoldus.com/api/insilico/v1.4/',
  'insilico-dev.knoldus.com': 'https://insilico-dev.knoldus.com/api/insilico/v1.4/',
  'insilico-staging.knoldus.com': 'https://insilico-staging.knoldus.com/api/insilico/v1.4/',
  'insilico.knoldus.com': 'https://insilico.knoldus.com/api/insilico/v1.4/',
};

const config = {
  api: {
    base: api[hostname] || (window.location.protocol + '//' + hostname + '/baile/v2.0/'),
    tokenLabel: 'Authorization',
    tokenValue: token => 'Bearer ' + token,
    contentType: 'application/json; charset=UTF-8',
  },
  chart: {
    type: describeEnum(IDashboard.DashboardChartType, {
      labels: {
        BAR: 'Bar Chart',
        LINE: 'Line Chart',
        PIE: 'Pie Chart',
        SCATTER: 'Scatter',
        GEO: 'Geo',
        TABLE: 'Table',
        ONEDSCATTER: 'One-D Scatter',
      },
    }),
    options: {
      orientation: {
        values: {
          HORIZONTAL: 'HORIZONTAL',
          VERTICAL: 'VERTICAL',
        },
        list: ['HORIZONTAL', 'VERTICAL'],
        labels: {
          HORIZONTAL: 'Horizontal',
          VERTICAL: 'Vertical',
        },
      },
      drawMethod: describeEnum(DashboardCharts.DrawMethod, {
        labels: {
          AREA: 'Area',
          BUBBLE: 'Bubble',
        },
      }),
      geoType: describeEnum(DashboardCharts.GeoHeatMapType, {
        labels: {
          STATE: 'State Code',
          COUNTY: 'County Name',
          ZIP: 'Zip Code',
        },
      }),
      aggregationType: describeEnum(IDashboard.DashboardAggregationType, {
        labels: {
          SUM: 'SUM',
          MIN: 'MIN',
          MAX: 'MAX',
          AVG: 'AVG',
          COUNT: 'COUNT',
          NO_AGGREGATE: 'NO AGGREGATION',
        },
      }),
      type: {
        values: {
          STACKED: 'STACKED',
          GROUPED: 'GROUPED',
        },
        list: ['GROUPED', 'STACKED'],
        labels: {
          STACKED: 'Stacked',
          GROUPED: 'Grouped',
        },
      },
      gamma: describeEnum(DashboardCharts.Gamma, {
        labels: {
          'RAINBOW': 'Rainbow',
          'MAGMA': 'Magma',
          'BLUES': 'Blues',
          'GREENS': 'Greens',
          'REDS': 'Reds',
          'GREYS': 'Greys',
          'RED_BLUE': 'Red/Blue',
        },
      }),
    },
  },
  library: {
    scope: {
      values: {
        ALL: 'all',
        PERSONAL: 'personal',
        CORPORATE: 'corporate',
        SHARED: 'shared',
        RECENT: 'recent',
      },
      labels: {
        'all': 'All',
        'personal': 'Personal',
        'corporate': 'Corporate',
        'shared': 'Shared',
        'recent': 'Recent',
      },
      list: [
        'all', 'personal', /*'corporate',*/ 'shared', /*'recent',*/
      ],
    },
  },
  storage: {
    token: 'token', // storage key for token (lets keep it separately)
  },
  routes: {
    signin: '/signin',
    signout: '/signout',
    signinRedirect: '/desk/library',
    signoutRedirect: '/',
  },
  validatorErrors: {
    notEqual: 'Field should differ from "%value%"',
    max: 'Value should not be greater than %max%, %value% given',
    min: 'Value should not be lower than %min%, %value% given',
    float: '"%value%" is not a valid number',
    number: '"%value%" is not a valid integer',
    required: 'This field is required',
    password: 'Password must be at least 10 characters long and must include upper and lower case letters and digits',
    equals: 'This field should equal to %requiredValue%',
    errorMsg: '%text%',
    packageVersion: 'Version format should follow semantic versioning scheme: Major.Minor.Patch, eg: 1.10.0',
    packageName: 'Field should contains only lowercase letters, digits and hyphen',
    enum: '"%value%" is not allowed',
  },
  asset: describeEnum(IAsset.Type, { // aka asset types
    labels: {
      'FLOW': 'Flow',
      'TABLE': 'Table',
      'MODEL': 'Model',
      'PREDICTION': 'Prediction',
      'REPLAY': 'Replay',
      'TABLE_STATS': 'Table Stats',
      'ALBUM': 'Album',
      'CV_MODEL': 'CV Model',
      'CV_PREDICTION': 'CV Prediction',
      'DASHBOARD': 'Dashboard',
      'OPTIMIZATION': 'Optimization',
      'DIAA': 'DIAA',
      'ONLINE_JOB': 'Online Job',
      'ONLINE_API': 'Online API',
      'S9_PROJECT': 'S9 Project',
      'PIPELINE': 'Pipeline',
      'EXPERIMENT': 'Experiment',
      'DATASET': 'Binary Dataset',
      'SCRIPT_DEPLOYMENT': 'External Deployment',
    },
    labelsPlural: {
      'FLOW': 'Flows',
      'TABLE': 'Tables',
      'MODEL': 'Models',
      'PREDICTION': 'Predictions',
      'REPLAY': 'Replays',
      'TABLE_STATS': 'Table Stats',
      'ALBUM': 'Albums',
      'CV_MODEL': 'CV Models',
      'CV_PREDICTION': 'CV Predictions',
      'DASHBOARD': 'Dashboards',
      'OPTIMIZATION': 'Optimizations',
      'DIAA': 'DIAAs',
      'ONLINE_JOB': 'Online Jobs',
      'ONLINE_API': 'Online APIs',
      'S9_PROJECT': 'S9 Projects ',
      'PIPELINE': 'Pipelines',
      'EXPERIMENT': 'Experiments',
      'DATASET': 'Binary Datasets',
      'SCRIPT_DEPLOYMENT': 'External Deployments',
    },
    aliases: {
      'FLOW': 'flow',
      'TABLE': 'table',
      'MODEL': 'model',
      'PREDICTION': 'prediction',
      'REPLAY': 'replay',
      'TABLE_STATS': 'table-stats',
      'ALBUM': 'album',
      'CV_MODEL': 'cv-model',
      'CV_PREDICTION': 'cv-prediction',
      'DASHBOARD': 'dashboard',
      'OPTIMIZATION': 'optimization',
      'DIAA': 'diaa',
      'ONLINE_JOB': 'online-job',
      'ONLINE_API': 'online-api',
      'S9_PROJECT': 's9-project',
      'PIPELINE': 'pipeline',
      'EXPERIMENT': 'experiment',
      'DATASET': 'dataset',
      'SCRIPT_DEPLOYMENT': 'script-deployment',
    },
    aliasesPlural: {
      'FLOW': 'flows',
      'TABLE': 'tables',
      'MODEL': 'models',
      'PREDICTION': 'predictions',
      'REPLAY': 'replays',
      'TABLE_STATS': 'table-stats',
      'ALBUM': 'albums',
      'CV_MODEL': 'cv-models',
      'CV_PREDICTION': 'cv-predictions',
      'DASHBOARD': 'dashboards',
      'OPTIMIZATION': 'optimizations',
      'DIAA': 'diaas',
      'ONLINE_JOB': 'online-jobs',
      'ONLINE_API': 'online-apis',
      'S9_PROJECT': 's9-projects',
      'PIPELINE': 'pipelines',
      'EXPERIMENT': 'experiments',
      'DATASET': 'datasets',
      'SCRIPT_DEPLOYMENT': 'script-deployments',
    },
  }),
  binaryDataset: {
    status: describeEnum(BinaryDataset.Status, {
      labels: {
        IDLE: 'Idle',
        IMPORTING: 'Importing',
        EXPORTING: 'Exporting',
        ERROR: 'Error',
      },
      hasProcess: {
        IDLE: false,
        IMPORTING: true,
        EXPORTING: true,
        ERROR: false,
      },
      styles: {
        IDLE: 'dot-idle',
        EXPORTING: 'dot-warning',
        IMPORTING: 'dot-warning',
        ERROR: 'dot-danger',
      },
    }),
  },
  album: {
    labelMode: describeEnum(IAlbum.LabelMode, {
      labels: {
        LOCALIZATION: 'Object Detection',
        CLASSIFICATION: 'Classification',
      },
    }),
    type: describeEnum(IAlbum.Type, {
      labels: {
        SOURCE: 'Source',
        DERIVED: 'Play Results',
        TRAINRESULTS: 'Train Results',
      },
    }),
    status: describeEnum(IAlbum.Status, {
      labels: {
        ACTIVE: 'Active',
        SAVING: 'Saving',
        UPLOADING: 'Uploading',
        ERROR: 'Error',
      },
      hasProcess: {
        ACTIVE: false,
        SAVING: true,
        UPLOADING: true,
        ERROR: false,
      },
      styles: {
        ACTIVE: 'dot-idle',
        SAVING: 'dot-warning',
        UPLOADING: 'dot-warning',
        ERROR: 'dot-danger',
      },
    }),
  },
  flow: {
    status: describeEnum(IFlow.Status, {
      labels: {
        NEW: 'New',
        RUNNING: 'Running',
        ERROR: 'Error',
        PARTLY_DONE: 'Partly done',
        DONE: 'Done',
        DELETED: 'Deleted',
        QUEUED: 'Queued',
      },
      styles: {
        NEW: 'dot-ready',
        RUNNING: 'dot-warning',
        ERROR: 'dot-danger',
        PARTLY_DONE: 'dot-warning',
        DONE: 'dot-idle',
        DELETED: 'dot-danger',
        QUEUED: 'dot-warning',
      },
      hasProcess: {
        NEW: false,
        RUNNING: true,
        ERROR: false,
        PARTLY_DONE: false,
        DONE: false,
        DELETED: false,
        QUEUED: true,
      },
    }),
  },
  flowstep: {
    type: describeEnum(IFlowstep.Type, {
      labels: {
        insert: 'Insert',
        cluster: 'Cluster',
        filter: 'Filter',
        aggregate: 'Aggregate',
        join: 'Left Join',
        query: 'Query',
        window: 'Window',
        map: 'Rename',
        geojoin: 'Geospatial',
      },
      icons: {
        insert: 'iconapp iconapp-arrows',
        aggregate: 'iconapp iconapp-formula',
        join: 'iconapp iconapp-link',
        cluster: 'iconapp iconapp-dot',
        query: 'iconapp iconapp-map',
        filter: 'iconapp iconapp-round',
        window: 'iconapp iconapp-rotate',
        map: 'glyphicon glyphicon-list',
        geojoin: 'glyphicon glyphicon-globe',
      },
      features: {
        insert: [Feature.INSERT_TRANSFORMER],
        aggregate: [Feature.AGGREGATE_TRANSFORMER],
        join: [Feature.JOIN_TRANSFORMER],
        cluster: [Feature.CLUSTER_TRANSFORMER],
        query: [Feature.QUERY_TRANSFORMER],
        filter: [Feature.FILTER_TRANSFORMER],
        window: [Feature.WINDOW_TRANSFORMER],
        map: [Feature.MAP_TRANSFORMER],
        geojoin: [Feature.GEOJOIN_TRANSFORMER],
      },
    }),
    status: describeEnum(IFlowstep.Status, {
      labels: {
        NEW: 'NEW',
        RUNNING: 'RUNNING',
        ERROR: 'ERROR',
        PARTLY_DONE: 'PARTLY_DONE',
        DONE: 'DONE',
        DELETED: 'DELETED',
        QUEUED: 'QUEUED',
      },
    }),
    option: {
      aggregate: {
        type: {
          values: { // TODO: review values
            COUNT: 'count',
            COUNT_UNIQUE: 'count_unique',
            COUNT_IF: 'count_if',
            MAX: 'max',
            MIN: 'min', // ?
            SUM: 'sum',
            SUM_IF: 'sum_if',
            MEAN: 'mean',
            MEDIAN: 'median',
            AVERAGE_IF: 'average_if',
            DEVIATION: 'deviation',
          },
          labels: {
            'count': 'Count',
            'max': 'Max',
            'min': 'Min',
            'sum': 'Sum',
            'mean': 'Mean',
            'deviation': 'Deviation',
          },
          list: ['count', 'min', 'max', 'sum', 'mean', 'deviation'],
        },
      },
      join: {
        type: describeEnum(IFlowstep.JoinType, {
          labels: {
            NORMAL: 'Exact match',
            FUZZY: 'Fuzzy match',
          },
        }),
      },
      cluster: {
        type: describeEnum(IFlowstep.ClusterType, {
          labels: {
            K_MEANS: 'K-Means',
            FUZZY_C_MEANS: 'Fuzzy-C-Means',
          },
        }),
      },
      filter: {
        operator: Object.assign(describeEnum(IFlowstep.FilterRelationalOperator, {
            labels: {
              'EQ': '=',
              'NE': '!=',
              'LT': '<',
              'GT': '>',
              'GTE': '>=',
              'LTE': '<=',
              'CONTAINS': 'contains',
            },
          }),
          { booleanList: ['EQ', 'NE'] },
        ),
        booleanValue: {
          values: {
            TRUE: 't',
            FALSE: 'f',
          },
          labels: {
            't': 'true',
            'f': 'false',
          },
          list: ['t', 'f'],
        },
        operatorGroup: {
          values: {
            AND: 'AND',
            OR:  'OR',
          },
          labels: {
            'AND': 'AND',
            'OR':  'OR',
          },
          list: ['AND', 'OR'], // aka logicalOperationType
        },
      },
      window: {
        aggregator: {
          values: {
            AVG: 'avg',
            COUNT: 'count',
            CUME_DIST: 'cume_dist',
            DENSE_RANK: 'dense_rank',
            FIRST_VALUE: 'first_value',
            LAST_VALUE: 'last_value',
            LAG: 'lag',
            LEAD: 'lead',
            LISTAGG: 'listagg',
            MAX: 'max',
            MEDIAN: 'median',
            MIN: 'min',
            NTH_VALUE: 'nth_value',
            NTILE: 'ntile',
            PERCENT_RANK: 'percent_rank',
            PERCENTILE_CONT: 'percentile_cont',
            PERCENTILE_DISC: 'percentile_disc',
            RANK: 'rank',
            RATIO_TO_REPORT: 'ratio_to_report',
            ROW_NUMBER: 'row_number',
            STDDEV: 'stddev',
            STDDEV_SAMP: 'stddev_samp',
            STDDEV_POP: 'stddev_pop',
            SUM: 'sum',
            VARIANCE: 'variance',
            VAR_SAMP: 'var_samp',
            VAR_POP: 'var_pop',
          },
          list: [
            'avg', 'count', 'cume_dist', 'dense_rank', 'first_value', 'last_value',
            'lag', 'lead', 'listagg', 'max', 'median', 'min', 'nth_value', 'ntile',
            'percent_rank', 'percentile_cont', 'percentile_disc', 'rank', 'ratio_to_report',
            'row_number', 'stddev', 'stddev_samp', 'stddev_pop', 'sum', 'variance',
            'var_samp', 'var_pop',
          ],
          options: {
            'median': ['aggregatorArg', 'windowUpperBound'],
            'avg': ['aggregatorArg', 'orderBy', 'windowUpperBound'],
            'count': ['aggregatorArg', 'orderBy', 'windowUpperBound'],
            'cume_dist': ['orderBy'],
            'dense_rank': ['orderBy'],
            'first_value': ['aggregatorArg', 'ignoreNulls', 'respectNulls', 'orderBy', 'windowUpperBound'],
            'last_value': ['aggregatorArg', 'ignoreNulls', 'respectNulls', 'orderBy', 'windowUpperBound'],
            'lag': ['aggregatorArg', 'ignoreNulls', 'respectNulls', 'offset', 'orderBy'],
            'lead': ['aggregatorArg', 'ignoreNulls', 'respectNulls', 'offset', 'orderBy'],
            'listagg': ['aggregatorArg', 'listaggDelimiter', 'withinGroupExpression'],
            'max': ['aggregatorArg', 'orderBy', 'windowUpperBound'],
            'min': ['aggregatorArg', 'orderBy', 'windowUpperBound'],
            'nth_value': ['aggregatorArg', 'orderBy', 'offset', 'ignoreNulls', 'respectNulls', 'windowUpperBound'],
            'ntile': ['ntileGroupsCount', 'orderBy'],
            'percentile_cont': ['percentile', 'withinGroupExpression', 'windowUpperBound'],
            'percentile_disc': ['percentile', 'withinGroupExpression', 'windowUpperBound'],
            'percent_rank': ['orderBy', 'windowUpperBound'],
            'rank': ['orderBy', 'windowUpperBound'],
            'row_number': ['orderBy', 'windowUpperBound'],
            'ratio_to_report': [],
            'stddev': ['aggregatorArg', 'orderBy', 'windowUpperBound'],
            'stddev_samp': ['aggregatorArg', 'orderBy', 'windowUpperBound'],
            'stddev_pop': ['aggregatorArg', 'orderBy', 'windowUpperBound'],
            'sum': ['aggregatorArg', 'orderBy', 'windowUpperBound'],
            'variance': ['aggregatorArg', 'orderBy', 'windowUpperBound'],
            'var_samp': ['aggregatorArg', 'orderBy', 'windowUpperBound'],
            'var_pop': ['aggregatorArg', 'orderBy', 'windowUpperBound'],
          },
        },
      },
      geojoin: {
        relation: {
          values: {
            ST_DISTANCE: 'ST_DISTANCE',
            ST_CONTAINS: 'ST_CONTAINS',
          },
          labels: {
            'ST_DISTANCE': 'Distance',
            'ST_CONTAINS': 'Contains',
          },
          list: ['ST_DISTANCE', 'ST_CONTAINS'],
        },
        relationBase: {
          values: {
            LEFT: 'LEFT',
            RIGHT: 'RIGHT',
          },
          labels: {
            'LEFT': 'Left',
            'RIGHT': 'Right',
          },
          list: ['LEFT', 'RIGHT'],
        },
        geometry: {
          values: {
            POINT: 'POINT',
            POLYGON: 'POLYGON',
          },
          labels: {
            'POINT': 'Point',
            'POLYGON': 'Polygon',
          },
          list: ['POINT', 'POLYGON'],
        },
        operator: {
          values: {
            EG: 'EG',
            NE: 'NE',
            LT: 'LT',
            GT: 'GT',
            LTE: 'LTE',
            GTE: 'GTE',
          },
          labels: {
            'EG': '=',
            'NE': '!=',
            'LT': '<',
            'GT': '>',
            'LTE': '<=',
            'GTE': '>=',
          },
          list: ['EG', 'NE', 'LT', 'GT', 'LTE', 'GTE'],
        },
        relationsWithParameter: ['ST_DISTANCE'],
      },
    },
  },
  tableStats: {
    status: {
      values: {
        DONE: 'DONE',
        PENDING: 'PENDING',
        ERROR: 'ERROR',
      },
      labels: {
        'DONE': 'Done',
        'PENDING': 'Pending',
        'ERROR': 'Error',
      },
    },
  },
  table: {
    status: describeEnum(ITable.Status, {
      labels: {
        'ACTIVE': 'Active',
        'INACTIVE': 'Inactive',
        'SAVING': 'Saving',
        'ERROR': 'Error',
      },
      styles: {
        ACTIVE: 'dot-idle',
        INACTIVE: 'dot-danger',
        SAVING: 'dot-warning',
        ERROR: 'dot-danger',
      },
      hasProcess: {
        ACTIVE: false,
        INACTIVE: false,
        SAVING: true,
        ERROR: false,
      },
    }),
    datasetType: describeEnum(ITable.DatasetType, {
      labels: {
        SOURCE: 'Source',
        DERIVED: 'Derived',
      },
    }),
    column: {
      columnType: {
        values: {
          METRIC: 'METRIC',
          ATTRIBUTE: 'ATTRIBUTE',
        },
        labels: {
          METRIC: 'Metric',
          ATTRIBUTE: 'Attribute',
        },
        list: [
          'METRIC', 'ATTRIBUTE',
        ],
      },
      dataType: describeEnum(ITable.ColumnDataType, {
        values: { // maybe to make them lowercase? or uppercase?
          STRING: 'String',
          INTEGER: 'Integer',
          DOUBLE: 'Double',
          BOOLEAN: 'Boolean',
          TIMESTAMP: 'Timestamp',
          LONG: 'Long',
        },
        labels: {
          STRING: 'String',
          INTEGER: 'Integer',
          DOUBLE: 'Double',
          BOOLEAN: 'Boolean',
          TIMESTAMP: 'Timestamp',
          LONG: 'Long',
        },
        controlDataTypes: {
          STRING: 'text',
          INTEGER: 'number',
          DOUBLE: 'number',
          BOOLEAN: 'boolean',
          TIMESTAMP: 'text',
          LONG: 'number',
        },
        variableTypes: {
          STRING: ['Categorical'],
          INTEGER: ['Categorical', 'Continuous'],
          DOUBLE: ['Continuous'],
          BOOLEAN: ['Boolean'],
          TIMESTAMP: ['Categorical'],
          LONG: ['Categorical', 'Continuous'],
        },
        columnTypes: {
          STRING: ['ATTRIBUTE'],
          INTEGER: ['METRIC', 'ATTRIBUTE'],
          DOUBLE: ['METRIC', 'ATTRIBUTE'],
          BOOLEAN: ['METRIC', 'ATTRIBUTE'],
          TIMESTAMP: ['ATTRIBUTE'],
          LONG: ['METRIC', 'ATTRIBUTE'],
        },
        isNumeric: {
          STRING: false,
          INTEGER: true,
          DOUBLE: true,
          BOOLEAN: false,
          TIMESTAMP: false,
          LONG: true,
        },
      }),
      variableType: describeEnum(ITable.ColumnVariableType, {
        labels: {
          CONTINUOUS: 'Continuous',
          CATEGORICAL: 'Categorical',
        },
      }),
      align: describeEnum(ITable.ColumnAlign, {
        labels: {
          'LEFT': 'Left',
          'RIGHT': 'Right',
          'CENTER': 'Center',
        },
        htmlClass: {
          'LEFT': 'text-left',
          'RIGHT': 'text-right',
          'CENTER': 'text-center',
        },
      }),
    },
    'import': {
      extension: {
        list: ['.csv', '.xls', '.xlsx'],
      },
      sizeLimit: 3221225472, // 3 Gb
    },
    view: {
      pageSize: {
        tableView: 50,
        tableViewEmbed: 20,
      },
    },
  },
  model: {
    status: describeEnum(ITabularModel.Status, {
      labels: {
        ACTIVE: 'Active',
        TRAINING: 'Training',
        PREDICTING: 'Predicting',
        ERROR: 'Error',
        CANCELLED: 'Cancelled',
      },
      styles: {
        ACTIVE: 'dot-idle',
        TRAINING: 'dot-warning',
        PREDICTING: 'dot-warning',
        ERROR: 'dot-danger',
        CANCELLED: 'dot-cancelled',
      },
    }),
    type: {
      values: { // maybe to make them lowercase? or uppercase?
        LINEAR: 'Linear',
        LOGISTIC: 'Logistic',
      },
      labels: { // maybe to make them lowercase? or uppercase?
        'Linear': 'Linear',
        'Logistic': 'Logistic',
      },
      icons: {
        'Linear': 'icon-layers',
        'Logistic': 'icon-layers',
      },
      list: ['Linear', 'Logistic'],
    },
    column: {
      covariate: {
        values: {
          RESPONSE: 'response',
          PREDICTOR: 'predictor',
          IGNORE: 'ignore',
        },
        labels: {
          'response': 'Response',
          'predictor': 'Predictor',
          'ignore': 'Ignore',
        },
        list: ['response', 'predictor', 'ignore'],
      },
    },
  },
  cvModel: {
    status: describeEnum(ICVModel.Status, {
      labels: {
        ACTIVE: 'Active',
        SAVING: 'Saving',
        TRAINING: 'Training',
        PREDICTING: 'Predicting',
        ERROR: 'Error',
        CANCELLED: 'Cancelled',
      },
      styles: {
        ACTIVE: 'dot-idle',
        SAVING: 'dot-warning',
        TRAINING: 'dot-warning',
        PREDICTING: 'dot-warning',
        ERROR: 'dot-danger',
        CANCELLED: 'dot-cancelled',
      },
    }),
  },
  dashboard: {
    status: describeEnum(IDashboard.Status, {
      labels: {
        IDLE: 'OK',
      },
      styles: {
        IDLE: 'dot-idle',
      },
    }),
  },
  notification: { // inspired by http://tamerayd.in/ngToast/
    interval: 3000, // refresh interval in milliseconds
    level: {
      values: {
        INFO: 'info', // aka 'default'
        SUCCESS: 'success',
        WARNING: 'warning',
        DANGER: 'danger',
      },
    },
    type: {
      values: { // could be used directly as string values
        DEFAULT: 'DEFAULT',
        ERROR: 'ERROR',
        WARNING: 'WARNING',
        FILE_UPLOAD: 'FILE_UPLOAD',
        FLOW_PLAY: 'FLOW_PLAY',
        FLOWSTEP_PLAY: 'FLOWSTEP_PLAY',
      },
    },
    defaults: { // default per-item options
      timeout: 7000, // milliseconds
      dismissButton: false,
      dismissOnClick: true,
      onDismiss: null,
      pauseOnHover: true,
      // combineDuplications: false,
    },
  },
  process: {
    // pass
    interval: 5000, // milliseconds
    status: describeEnum(IProcess.Status, {
      labels: {
        'RUNNING': 'In Progress',
        'COMPLETED': 'Completed',
        'CANCELLED': 'Cancelled',
        'FAILED': 'Failed',
        'QUEUED': 'Queued',
      },
    }),
    job: {
      type: describeEnum(IProcess.JobType, {
        labels: {
          'S3_VIDEO_IMPORT': 'S3 Video Import',
          'S3_IMAGES_IMPORT': 'S3 Images Import',
          'TABULAR_UPLOAD': 'Tabular Upload',
          'ALBUM_AUGMENTATION': 'Album Augmentation',
          'CV_MODEL_TRAIN': 'CV Model Train',
          'CV_MODEL_PREDICT': 'CV Model Prediction',
          'CV_MODEL_EVALUATE': 'CV Model Evaluation',
          'TABULAR_PREDICT': 'Tabular Prediction',
          'TABULAR_TRAIN': 'Tabular Train',
          'TABULAR_EVALUATE': 'Tabular Evaluation',
          'TABULAR_COLUMN_STATISTICS': 'Tabular Column Statistics',
          'CV_MODEL_IMPORT': 'CV Model Import',
          'MERGE_ALBUM': 'Merge Album',
          'PROJECT_BUILD': 'S9 Project Building',
          'GENERIC_EXPERIMENT': 'Generic Experiment',
          'DATASET_IMPORT': 'Dataset Import',
          'DATASET_EXPORT': 'Dataset Export',
          'API_DEPLOYMENT': 'API Deployment',
          'SCRIPT_DEPLOYMENT': 'Script Deploying',
        },
        mockOnly: {
          'S3_VIDEO_IMPORT': false,
          'S3_IMAGES_IMPORT': false,
          'TABULAR_UPLOAD': false,
          'ALBUM_AUGMENTATION': false,
          'CV_MODEL_TRAIN': false,
          'CV_MODEL_PREDICT': false,
          'CV_MODEL_EVALUATE': false,
          'TABULAR_PREDICT': false,
          'TABULAR_TRAIN': false,
          'TABULAR_EVALUATE': false,
          'TABULAR_COLUMN_STATISTICS': false,
          'CV_MODEL_IMPORT': false,
          'MERGE_ALBUM': false,
          'PROJECT_BUILD': false,
          'GENERIC_EXPERIMENT': false,
          'DATASET_IMPORT': false,
          'DATASET_EXPORT': false,
          'API_DEPLOYMENT': true,
          'SCRIPT_DEPLOYMENT': true,
        },
      }),
    },
  },
  code: {
    mode: {
      SQL: 'sql',
      EXCEL: 'excel',
    },
  },
  dnd: {
    zone: {
      FLOW_TABLE_LIST: 'flow-table-list',
      FLOWSTEP_TABLE: 'flowstep-table',
    },
  },
  clusteringResults: {
    maxPoints: 40000,
    maxDimensions: 2,
  },
  graph: {
    flow: {
      maxScale: 1.5,
      templates: {
        inputNode: {
          shape: 'rect',
          rx: 0,
          ry: 0,
          'class': 'input-template',
        },
        outputNode: {
          shape: 'rect',
          rx: 0,
          ry: 0,
          'class': 'output-template',
        },
        stepNode: {
          shape: 'diamond',
          'class': 'operation-template',
        },
        inputEdge: { arrowhead: 'undirected' },
        outputEdge: { arrowhead: 'normal' },
      },
    },
  },
  prediction: {
    status: describeEnum(IPredictionStatus, {
      values: {
        NEW: 'NEW',
        RUNNING: 'RUNNING',
        ERROR: 'ERROR',
        DONE: 'DONE',
        INCOMPLETE: 'INCOMPLETE',
      },
      labels: {
        NEW: 'New',
        RUNNING: 'Running',
        ERROR: 'Error',
        DONE: 'DONE',
        INCOMPLETE: 'Incomplete',
      },
      styles: {
        NEW: 'dot-ready',
        RUNNING: 'dot-warning',
        ERROR: 'dot-danger',
        DONE: 'dot-idle',
        INCOMPLETE: 'dot-warning',
      },
    }),
  },
  cvPrediction: {
    status: describeEnum(ICVPredictionStatus, {
      values: {
        NEW: 'NEW',
        RUNNING: 'RUNNING',
        ERROR: 'ERROR',
        DONE: 'DONE',
        INCOMPLETE: 'INCOMPLETE',
      },
      labels: {
        NEW: 'New',
        RUNNING: 'Running',
        ERROR: 'Error',
        DONE: 'DONE',
        INCOMPLETE: 'Incomplete',
      },
      styles: {
        NEW: 'dot-ready',
        RUNNING: 'dot-warning',
        ERROR: 'dot-danger',
        DONE: 'dot-idle',
        INCOMPLETE: 'dot-warning',
      },
    }),
  },
  replay: {
    status: {
      values: {
        NEW: 'NEW',
        RUNNING: 'RUNNING',
        ERROR: 'ERROR',
        DONE: 'DONE',
        INCOMPLETE: 'INCOMPLETE',
      },
      labels: {
        'NEW': 'New',
        'RUNNING': 'Running',
        'ERROR': 'Error',
        'DONE': 'DONE',
        'INCOMPLETE': 'Incomplete',
      },
      styles: {
        NEW: 'dot-ready',
        RUNNING: 'dot-warning',
        ERROR: 'dot-danger',
        DONE: 'dot-idle',
        INCOMPLETE: 'dot-danger',
      },
    },
  },
  modal: {
    size: {
      LARGE: 'modal-lg',
      SMALL: 'modal-sm',
    },
  },
  pictureArchive: {
    'import': {
      extension: {
        list: ['.zip', '.tar', '.gz'],
      },
    },
  },
  picture: {
    'import': {
      extension: {
        list: ['.png', '.jpg', '.gif'],
      },
    },
    labels: {
      extension: {
        list: ['.csv'],
      },
    },
  },
  optimization: {
    status: {
      values: {
        RUNNING: 'RUNNING',
        ERROR: 'ERROR',
        DONE: 'DONE',
      },
      labels: {
        'RUNNING': 'Running',
        'ERROR': 'Error',
        'DONE': 'DONE',
      },
      styles: {
        'RUNNING': 'dot-warning',
        'ERROR': 'dot-danger',
        'DONE': 'dot-idle',
      },
    },
    type: describeEnum(IOptimization.OptimizationType, {
      labels: {
        PREDICTOR_TUNING: 'Predictor Tuning',
        OBJECTIVE_FUNCTION: 'Objective Function',
      },
    }),
    goal: describeEnum(IOptimization.ObjectiveGoal, {
      labels: {
        MIN: 'Min',
        MAX: 'Max',
      },
    }),
    metric: describeEnum(IOptimization.ObjectiveMetric, {
      labels: {
        AUROC: 'AUC',
        KS: 'KS',
        RMSE: 'RMSE',
      },
    }),
  },
  s9Project: {
    status: describeEnum(IS9Project.Status, {
      labels: {
        IDLE: 'Idle',
        BUILDING: 'Building',
        INTERACTIVE: 'Interactive',
      },
      hasProcess: {
        IDLE: false,
        BUILDING: true,
        INTERACTIVE: false,
      },
      styles: {
        IDLE: 'dot-idle',
        BUILDING: 'dot-warning',
        INTERACTIVE: 'dot-ready',
      },
    }),
    sessionStatus: describeEnum(IS9ProjectSession.Status, {
      labels: {
        QUEUED: 'Preparing...',
        SUBMITTED: 'Preparing...',
        RUNNING: 'Ready',
        COMPLETED: 'Closed',
        FAILED: 'Error',
      },
      styles: {
        QUEUED: 'label-warning',
        SUBMITTED: 'label-warning',
        RUNNING: 'label-success',
        COMPLETED: 'label-default',
        FAILED: 'label-danger',
      },
    }),
  },
  pipeline: {
    status: describeEnum({}, {
      labels: {
      },
      hasProcess: {
      },
      styles: {
      },
    }),
  },
  user: {
    status: describeEnum(UserStatus, {
      labels: {
        ACTIVE: 'Active',
        INACTIVE: 'Inactive',
        DEACTIVATED: 'Deactivated',
      },
    }),
    role: describeEnum(UserRole, {
      labels: {
        USER: 'User',
        ADMIN: 'Admin',
      },
    }),
  },
  primitives: {
    operatorType: describeEnum(CVTLModelPrimitiveType, {
      labels: {
        UTLP: 'UTLP',
        CLASSIFIER: 'Classifier',
        DETECTOR: 'Detector',
      },
    }),
  },
  experiments: {
    status: describeEnum(IExperiment.Status, {
      labels: {
        RUNNING: 'Running',
        COMPLETED: 'Completed',
        ERROR: 'Error',
        CANCELLED: 'Canceled',
      },
      hasProcess: {
        RUNNING: true,
        COMPLETED: false,
        ERROR: false,
        CANCELLED: false,
      },
      styles: {
        RUNNING: 'dot-warning',
        COMPLETED: 'dot-idle',
        ERROR: 'dot-error',
        CANCELLED: 'dot-cancelled',
      },
    }),
  },
  userInputDebounceTime: 500,
  defaultPageSize: 20,
  listAllChunkSize: 1000,
  REQUIRE_EMAIL_CONFIRMATION: true,
};

export default config;
