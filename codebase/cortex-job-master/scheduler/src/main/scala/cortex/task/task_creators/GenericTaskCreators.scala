package cortex.task.task_creators

import cortex.{ TaskParams, TaskResult }
import play.api.libs.json.{ Reads, Writes }

//scala doesn't have ability of "multiple same type inheritance",
//so we have to create separate trait for each task type
//probably it can be replaced with sth better looking

trait TrainTaskCreator[T <: TaskParams, TR <: TaskResult] {

  /** Docker image to be used */
  def dockerImage: String

  /** Module to be used in docker image */
  def module: String

  def trainTask(
    id:       String,
    jobId:    String,
    taskPath: String,
    params:   T,
    cpus:     Double,
    memory:   Double,
    gpus:     Int    = 0
  )(implicit reads: Reads[TR], writes: Writes[T]): GenericTask[TR, T] = {
    GenericTask[TR, T](id, jobId, params, cpus, memory, module, taskPath, dockerImage, gpus)
  }
}

trait ScoreTaskCreator[T <: TaskParams, TR <: TaskResult] {
  def dockerImage: String
  def module: String

  def scoreTask(
    id:       String,
    jobId:    String,
    taskPath: String,
    params:   T,
    cpus:     Double,
    memory:   Double,
    gpus:     Int    = 0
  )(implicit reads: Reads[TR], writes: Writes[T]): GenericTask[TR, T] = {
    GenericTask[TR, T](id, jobId, params, cpus, memory, module, taskPath, dockerImage, gpus)
  }
}

trait EvaluateTaskCreator[T <: TaskParams, TR <: TaskResult] {
  def dockerImage: String
  def module: String

  def evaluateTask(
    id:       String,
    jobId:    String,
    taskPath: String,
    params:   T,
    cpus:     Double,
    memory:   Double,
    gpus:     Int    = 0
  )(implicit reads: Reads[TR], writes: Writes[T]): GenericTask[TR, T] = {
    GenericTask[TR, T](id, jobId, params, cpus, memory, module, taskPath, dockerImage, gpus)
  }
}

trait PredictTaskCreator[T <: TaskParams, TR <: TaskResult] {
  def dockerImage: String
  def module: String

  def predictTask(
    id:       String,
    jobId:    String,
    taskPath: String,
    params:   T,
    cpus:     Double,
    memory:   Double,
    gpus:     Int    = 0
  )(implicit reads: Reads[TR], writes: Writes[T]): GenericTask[TR, T] = {
    GenericTask[TR, T](id, jobId, params, cpus, memory, module, taskPath, dockerImage, gpus)
  }
}

trait TrainPredictTaskCreator[T <: TaskParams, TR <: TaskResult] {
  def dockerImage: String
  def module: String

  def trainPredictTask(
    id:       String,
    jobId:    String,
    taskPath: String,
    params:   T,
    cpus:     Double,
    memory:   Double,
    gpus:     Int    = 0
  )(implicit reads: Reads[TR], writes: Writes[T]): GenericTask[TR, T] = {
    GenericTask[TR, T](id, jobId, params, cpus, memory, module, taskPath, dockerImage, gpus)
  }
}

trait TransformTaskCreator[T <: TaskParams, TR <: TaskResult] {
  def dockerImage: String
  def module: String

  def transformTask(
    id:       String,
    jobId:    String,
    taskPath: String,
    params:   T,
    cpus:     Double,
    memory:   Double,
    gpus:     Int    = 0
  )(implicit reads: Reads[TR], writes: Writes[T]): GenericTask[TR, T] = {
    GenericTask[TR, T](id, jobId, params, cpus, memory, module, taskPath, dockerImage, gpus)
  }
}
