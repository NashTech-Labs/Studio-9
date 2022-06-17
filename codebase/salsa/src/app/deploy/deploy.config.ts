import { describeEnum } from '../utils/misc';

import { IOnlineAPI } from './online-api.interface';
import { IOnlineTriggeredJob } from './online-job.interface';
import { IScriptDeployment } from './script-deployment.interface';

export const deployConfig = {
  onlineJob: {
    status: describeEnum(IOnlineTriggeredJob.Status, {
      labels: {
        IDLE: 'Idle',
        RUNNING: 'Running',
      },
      styles: {
        IDLE: 'dot-idle',
        RUNNING: 'dot-warning',
      },
    }),
  },
  onlineAPI: {
    status: describeEnum(IOnlineAPI.Status, {
      labels: {
        ACTIVE: 'Active',
        PREPARING: 'Preparing',
        INACTIVE: 'Inactive',
      },
      styles: {
        ACTIVE: 'dot-ready',
        PREPARING: 'dot-warning',
        INACTIVE: 'dot-cancelled',
      },
    }),
  },
  scriptDeployment: {
    status: describeEnum(IScriptDeployment.Status, {
      labels: {
        PREPARING: 'Preparing',
        READY: 'Ready',
      },
      styles: {
        PREPARING: 'dot-warning',
        READY: 'dot-idle',
      },
      hasProcess: {
        PREPARING: true,
        READY: false,
      },
    }),
    mode: describeEnum(IScriptDeployment.Mode, {
      labels: {
        CV_PREDICTION: 'CV Model Prediction',
        CV_3STL_DETECTION: 'Three-step TL Prediction',
      },
      disabled: {
        CV_PREDICTION: true,
        CV_3STL_DETECTION: false,
      },
    }),
    hardwareMode: describeEnum(IScriptDeployment.HardwareMode, {
      labels: {
        CPU: 'CPU only',
        CUDA: 'CUDA-enabled GPU',
        INTEL_NCS2: 'Mobile Edge Device',
      },
      disabled: {
        CPU: true,
        CUDA: true,
        INTEL_NCS2: false,
      },
    }),
  },
};
