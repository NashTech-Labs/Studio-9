help:
	@echo "test (run tests)"
	@echo "test-it (run IT tests)"
	@echo "test-ete (run E2E tests)"
	@echo "test-bench (run benchmark tests)"
	@echo "test-all (run all tests)"
	@echo "scalastyle (examines your Scala code)"
	@echo "codacy-coverage (update Codacy code coverage)"
	@echo "get-version (get the current version of the project)"
	@echo "get-jm-version (get the current version of job-master, i.e. commit hash)"
	@echo "deploy-p (deploy in production)"

test:
	sbt test

test-it:
	sbt it:test

test-ete:
	sbt ete:test

test-bench:
	sbt bench:test

test-all:
	sbt scalastyle
	sbt testAll

scalastyle:
	sbt scalastyle

codacy-coverage:
	sbt clean coverage testAll
	sbt coverageReport
	sbt coverageAggregate
	export CODACY_PROJECT_TOKEN=$(CORTEX_JOB_MASTER_CODACY_PROJECT_TOKEN); sbt codacyCoverage

get-version:
	sbt "project cortex-job-master" cortex-job-master/version

get-jm-version:
	sbt "project cortex-job-master" "run version"

deploy-p:
	sbt "project cortex-job-master" "release with-defaults"
