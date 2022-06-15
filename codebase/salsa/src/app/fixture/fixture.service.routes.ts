import { IFixtureServiceRoute } from './fixture.interface';
import { albumsRoutes } from './routes/albums.routes';
import { composeRoutes } from './routes/compose.routes';
import { coreRoutes } from './routes/core.routes';
import { cvModelsRoutes } from './routes/cv-models.routes';
import { cvPredictionsRoutes } from './routes/cv-predictions.routes';
import { datasetsRoutes } from './routes/datasets.routes';
import { s9ProjectsRoutes } from './routes/s9-projects.routes';
import { diaaRoutes } from './routes/diaa.routes';
import { experimentsRoutes } from './routes/experiments.routes';
import { modelsRoutes } from './routes/models.routes';
import { onlineApisRoutes } from './routes/online-apis.routes';
import { onlineJobsRoutes } from './routes/online-jobs.routes';
import { optimizationsRoutes } from './routes/optimizations.routes';
import { packagesRoutes } from './routes/packages.routes';
import { pipelinesRoutes } from './routes/pipelines.routes';
import { predictionsRoutes } from './routes/predictions.routes';
import { processesRoutes } from './routes/processes.routes';
import { projectsRoutes } from './routes/projects.routes';
import { replaysRoutes } from './routes/replays.routes';
import { scriptDeploymentsRoutes } from './routes/script-deployments.routes';
import { sharesRoutes } from './routes/shares.routes';
import { tablesRoutes } from './routes/tables.routes';
import { usersRoutes } from './routes/users.routes';
import { visualizeRoutes } from './routes/visualize.routes';

export const routes: IFixtureServiceRoute[] = [
  /* Example:
   {
   url: 'string or regex string',
   method: 'GET',
   handler: function(this, data) {
   const doc = users.findOne({email: data.email; password: data.password});
   return doc;
   }
   }
   */
  ...coreRoutes,
  ...sharesRoutes,
  ...processesRoutes,
  ...composeRoutes,
  ...tablesRoutes,
  ...albumsRoutes,
  ...datasetsRoutes,
  ...projectsRoutes,
  ...modelsRoutes,
  ...cvModelsRoutes,
  ...predictionsRoutes,
  ...cvPredictionsRoutes,
  ...replaysRoutes,
  ...optimizationsRoutes,
  ...visualizeRoutes,
  ...diaaRoutes,
  ...onlineJobsRoutes,
  ...onlineApisRoutes,
  ...scriptDeploymentsRoutes,
  ...s9ProjectsRoutes,
  ...usersRoutes,
  ...packagesRoutes,
  ...pipelinesRoutes,
  ...experimentsRoutes,
];
