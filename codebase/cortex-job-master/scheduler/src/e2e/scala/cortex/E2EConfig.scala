package cortex

import com.typesafe.config.ConfigFactory
import cortex.task.TabularAccessParams.{ DremioAccessParams, RedshiftAccessParams }
import cortex.task.tabular_data.Table

//noinspection TypeAnnotation
trait E2EConfig {
  private lazy val configFactory = ConfigFactory.load()
  private lazy val redshiftConfig = configFactory.getConfig("redshift")
  private lazy val dremioConfig = configFactory.getConfig("dremio")
  private lazy val s3Config = configFactory.getConfig("s3")
  private lazy val fixtures = configFactory.getConfig("fixtures")

  //redshift
  lazy val redshiftAccessParams = RedshiftAccessParams(
    database = redshiftConfig.getString("db"),
    hostname = redshiftConfig.getString("host"),
    port = redshiftConfig.getInt("port"),
    username = redshiftConfig.getString("user"),
    password = redshiftConfig.getString("password"),
    s3IamRole = "role_id"
  )

  //dremio
  lazy val dremioAccessParams = DremioAccessParams(
    url = dremioConfig.getString("url"),
    username = dremioConfig.getString("username"),
    password = dremioConfig.getString("password"),
    source = dremioConfig.getString("source"),
    namespace = dremioConfig.getString("namespace"),
    s3TablesDir = s3Config.getString("tables-dir")
  )
  lazy val dremioExportingChunksize = dremioConfig.getInt("chunksize")

  //s3
  lazy val s3AccessKey = s3Config.getString("access-key")
  lazy val s3SecretKey = s3Config.getString("secret-key")
  lazy val s3Bucket = s3Config.getString("bucket")
  lazy val s3Region = s3Config.getString("region")

  //fixtures
  lazy val redshiftTestTable = Table(
    schema = fixtures.getString("redshift-test-table.schema"),
    name = fixtures.getString("redshift-test-table.name")
  )
  lazy val dremioTestTable = Table(
    schema = fixtures.getString("dremio-test-table.schema"),
    name = fixtures.getString("dremio-test-table.name")
  )
  lazy val redshiftDataSample = s"s3://$s3Bucket/cortex-job-master/e2e/redshift_data_sample.txt"
  lazy val dremioDataSample = s"cortex-job-master/e2e/dremio_data_sample.csv"
  lazy val s3TestPath = fixtures.getString("s3-test-path")
  lazy val abaloneTrainTable = Table(
    schema = "devuser1_e4475008_a1b0_4e22_9103_633bd1f1b437_default",
    name = "abalone_train"
  )
  lazy val abaloneEvaluateTable = Table(
    schema = "devuser1_e4475008_a1b0_4e22_9103_633bd1f1b437_default",
    name = "abalone_evaluate"
  )
  lazy val abaloneOutTable = Table(
    schema = "devuser1_e4475008_a1b0_4e22_9103_633bd1f1b437_default",
    name = "e2e_abalone"
  )
}

//script for generating abalone evaluate
//create table "devuser1_e4475008_a1b0_4e22_9103_633bd1f1b437_default"."abalone_evaluate" as (select
//t1.logrings as logrings_new,
//t1.sex as sex_new,
//t1.length as length_new,
//t1.diameter as diameter_new,
//t1.height as height_new,
//t1.whole_weight as whole_weight_new,
//t1.shucked_weight as shucked_weight_new,
//t1.viscera_weight as viscera_weight_new,
//t1.shell_weight as shell_weight_new from "devuser1_e4475008_a1b0_4e22_9103_633bd1f1b437_default"."abalone_train" as t1);
