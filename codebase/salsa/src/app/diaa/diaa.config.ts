import { describeEnum } from '../utils/misc';

import { IDIAA } from './diaa.interface';

export const diaaConfig = {
  diaaObjectives: describeEnum(IDIAA.DIAAObjective, {
    labels: {
      'AIR': 'AIR',
      'SMD': 'SMD',
    },
    metricLabels: {
      AIR: 'AIR Metrics',
      SMD: 'SMD Metrics',
    },
    maximizingLabels: {
      AIR: 'AIRs maximizing',
      SMD: 'SMDs maximizing',
    },
  }),
  status: describeEnum(IDIAA.Status, {
    labels: {
      'CHECKING': 'Checking',
      'CHECKED': 'Checked',
      'RUNNING': 'Running',
      'ERROR': 'Error',
      'DONE': 'DONE',
    },
    styles: {
      'CHECKING': 'dot-warning',
      'CHECKED': 'dot-ready',
      'RUNNING': 'dot-warning',
      'ERROR': 'dot-danger',
      'DONE': 'dot-idle',
    },
  }),
  datasetRef: describeEnum(IDIAA.DatasetRef, {
    labels: {
      'INPUT': 'Input',
      'HOLD_OUT': 'Hold-Out',
      'OUT_OF_TIME': 'Out-of-Time',
    },
  }),
  objectiveMetric: describeEnum(IDIAA.ObjectiveMetric, {
    labels: {
      'RACE_BLACK': 'African-American',
      'RACE_HISPANIC': 'Hispanic',
      'RACE_ASIAN': 'Asian',
      'GENDER_FEMALE': 'Female',
      'AGE_OLDER': 'Older',
    },
  }),
  constraintMetric: describeEnum(IDIAA.ConstraintMetric, {
    labels: {
      'RACE_BLACK': 'African-American',
      'RACE_HISPANIC': 'Hispanic',
      'RACE_ASIAN': 'Asian',
      'GENDER_FEMALE': 'Female',
      'AGE_OLDER': 'Older',
      'AUROC': 'AUC',
      'KS': 'KS',
    },
  }),
  constraintOperator: describeEnum(IDIAA.ConstraintOperator, {
    labels: {
      'EQ': '=',
      'NE': '!=',
      'LT': '<',
      'GT': '>',
      'T20': 'within +-20%',
      'T10': 'within +-10%',
      'T05': 'within +-5%',
      'T01': 'within +-1%',
    },
  }),
};
