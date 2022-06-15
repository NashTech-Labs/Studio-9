help:
	@echo "test (run tests)"
	@echo "test-it (run IT tests)"
	@echo "test-ete (run E2E tests)"
	@echo "test-bench (run benchmark tests)"
	@echo "test-all (run all tests)"
	@echo "codacy-coverage (update Codacy code coverage)"
	@echo "run-l (run locally)"
	@echo "get-version (get the current version of the project)"
	@echo "docker-stage (publish docker artifacts to ./api-rest/target/docker/ folder)"
	@echo "docker-publish (publish docker image to docker hub)"
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
	sbt testAll

codacy-coverage:
	sbt clean coverage testAll
	sbt coverageReport
	sbt coverageAggregate
	export CODACY_PROJECT_TOKEN=$(ORION_CODACY_PROJECT_TOKEN);
	sbt codacyCoverage

run-l:
    docker run --rm --name some-zookeeper -d -p 2181:2181 zookeeper
	sbt "project orion-api-rest" run

get-version:
	sbt "project orion-api-rest" orion-api-rest/version

docker-stage:
	@echo "will publish docker artifacts to ./api-rest/target/docker/ folder"
	sbt "project orion-api-rest" docker:stage

docker-publish:
	sbt "project orion-api-rest" docker:publish

deploy-p:
	sbt testAll
	sbt "project orion-api-rest" "release with-defaults"
