import { IProcess } from '../core/interfaces/process.interface';

export interface IProcessSearchParams {
  page?: number;
  page_size?: number;
  order?: string;
  jobTypes?: IProcess.JobType[];
  processStarted?: [string, string];
  processCompleted?: [string, string];
}
