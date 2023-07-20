sinclude .env # create from example.env
PROJECT ?= nf-quilt
WRITE_BUCKET ?= quilt-example
FRAGMENT ?= &path=.
NF_DIR ?= ../nextflow
PID ?= $$$$
PIP ?= python -m pip
PIPELINE ?= sarek
QUERY ?= ?Name=$(USER)&Owner=Kevin+Moore&Date=2023-03-07&Type=CRISPR&Notebook+URL=http%3A%2F%2Fexample.com
TEST_URI ?= quilt+s3://$(WRITE_BUCKET)$(QUERY)\#package=test/hurdat$(FRAGMENT)
QUILT_URI ?=  quilt+s3://$(WRITE_BUCKET)\#package=$(PROJECT)/$(PIPELINE)
PIP ?= pip3
REPORT ?= ./plugins/$(PROJECT)/build/reports/tests/test/index.html

verify: #compile
	echo $(WRITE_BUCKET)
	./gradlew check || open $(REPORT)

check-env:
	echo $(WRITE_BUCKET)
	echo "$(TEST_URI)"
	echo "Use 'make WRITE_BUCKET=<value>' to override" 
	printenv MAKEFLAGS

clean:
	./gradlew clean
	rm -rf work null results
	rm -rf */*/build plugins/nf-quilt/bin
	rm -f .nextflow.log* .launch*classpath

compile:
	./gradlew compileGroovy exportClasspath
	@echo "DONE `date`"

nextflow:
	if [ ! -d "$(NF_DIR)" ]; then git clone https://github.com/nextflow-io/nextflow.git  "$(NF_DIR)"; fi
	pushd "$(NF_DIR)"; git checkout && make compile && git restore .; popd

install-python:
	if ! which quilt3; then $(PIP) install quilt3 ; fi
	which quilt3

compile-all: install-python nextflow compile

check:
	./gradlew check --warning-mode all

coverage: compile
	./gradlew cloverInstrumentCodeForTest

.PHONY: clean test test-all all pkg-test tower-test

test: clean compile check #coverage

test-nextflow: clean nextflow compile check #coverage

test-all: clean compile-all check #coverage

#
# Create packages
#

pkg-test: compile-all
	echo "$(TEST_URI)"
	./launch.sh run ./main.nf -profile standard -plugins $(PROJECT) --outdir "$(TEST_URI)"

pkg-fail: compile
	echo "$(TEST_URI)"
	./launch.sh run ./fail.nf -profile standard -plugins $(PROJECT) --outdir "$(TEST_URI)"

tower-test: compile-all
	nextflow run "https://github.com/quiltdata/nf-quilt" -name local_einstein  -with-tower -r main -latest --pub "$(TEST_URI)"

# use `make $(PIPELINE) WRITE_BUCKET=my-s3-bucket` to publish `--outdir` to a Quilt package

$(PIPELINE): compile-all
	echo "Ensure you have docker running"
	./launch.sh pull nf-core/$(PIPELINE)
	./launch.sh run nf-core/$(PIPELINE) -profile test,docker -plugins $(PROJECT) --outdir "$(QUILT_URI)"

#
# Show dependencies
#

deps:
	./gradlew -q ${mm}dependencies
# 	echo "try 'make deps CONFIG=runtimeClasspath' or 'make deps CONFIG=groovyDoc'"
# 	./gradlew -q ${mm}dependencies --configuration ${CONFIG}

deps-all:
	./gradlew -q dependencyInsight
#	./gradlew -q ${mm}dependencies --configuration ${CONFIG}  --dependency ${module}

#
# Refresh SNAPSHOTs dependencies
#
refresh:
	./gradlew --refresh-dependencies dependencies

#
# Run all tests or selected ones
#
#test:
#	./gradlew ${mm}test

test-class:
	./gradlew ${mm}test --tests ${class}

fast:
	./gradlew ${mm}test --fail-fast || open ./plugins/nf-quilt/build/reports/tests/test/index.html

#
# Upload JAR artifacts to Maven Central

publish:
	echo "Ensure you have set 'github_organization=<owner>' in gradle.properties"
	ls gradle.properties # create locally or globally if it does not exist
	./gradlew :plugins:$(PROJECT):upload
	./gradlew :plugins:publishIndex
