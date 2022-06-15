help:
	@echo "test (run tests)"
	@echo "test-it (run IT tests)"
	@echo "test-ete (run E2E tests)"
	@echo "test-bench (run benchmark tests)"
	@echo "test-all (run all tests)"
	@echo "scalastyle (examines your Scala code)"
	@echo "codacy-coverage (update Codacy code coverage)"
	@echo "run-l (run locally)"
	@echo "get-version (get the current version of the project)"
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
	export CODACY_PROJECT_TOKEN=$(ORION_IPC_CODACY_PROJECT_TOKEN); sbt codacyCoverage

run-l:
	sbt "project orion-ipc-rabbitmq" run

get-version:
	sbt "project orion-ipc-rabbitmq" orion-ipc-rabbitmq/version

deploy-p:
	sbt testAll "project orion-ipc-rabbitmq" "release with-defaults"
