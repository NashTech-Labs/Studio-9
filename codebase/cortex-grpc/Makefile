SHELL := /bin/bash

install:
	ls venv || virtualenv venv -p python3.6
	source venv/bin/activate; \
	    pip install -r requirements.txt s3pypi==0.7.4.dev0 --extra-index-url ${PYTHON_ARTIFACTS_INDEX_URL}

clean:
	rm -rf dist build ./*.egg-info cortex

build: clean install
	source venv/bin/activate; scripts/gen_python_code.sh

publish-python: build
	source venv/bin/activate; scripts/deploy_python.sh

publish-scala:
	sbt +publish

publish: publish-scala publish-python
