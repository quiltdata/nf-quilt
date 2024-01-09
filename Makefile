sinclude .env # create from example.env
PROJECT ?= nf-quilt
WRITE_BUCKET ?= quilt-example
FRAGMENT ?= &path=.
NF_DIR ?= ../nextflow
NF_BIN ?= ./nextflow
PID ?= $$$$
PIPELINE ?= sarek
QUERY ?= ?Name=$(USER)&Owner=Kevin+Moore&Date=2023-03-07&Type=CRISPR&Notebook+URL=http%3A%2F%2Fexample.com
TEST_URI ?= quilt+s3://$(WRITE_BUCKET)$(QUERY)\#package=test/hurdat$(FRAGMENT)
QUILT_URI ?=  quilt+s3://$(WRITE_BUCKET)\#package=$(PROJECT)/$(PIPELINE)
REPORT ?= ./plugins/$(PROJECT)/build/reports/tests/test/index.html
VERSION ?= $(shell grep 'Plugin-Version' plugins/$(PROJECT)/src/resources/META-INF/MANIFEST.MF | awk '{ print $$2 }')

verify: #compile
	echo $(WRITE_BUCKET)
	./gradlew check || open $(REPORT)

fast:
	./gradlew ${mm}test --fail-fast || open ./plugins/nf-quilt/build/reports/tests/test/index.html

check-env:
	echo $(VERSION)
	echo $(WRITE_BUCKET)
	echo "$(TEST_URI)"
	echo "Use 'make WRITE_BUCKET=<value>' to override" 
	printenv MAKEFLAGS

clean:
	./gradlew clean
	rm -rf null results
	rm -rf */*/build plugins/nf-quilt/bin
	rm -f .nextflow.log* .launch*classpath

compile:
	./gradlew compileGroovy exportClasspath
	@echo "DONE `date`"

$(NF_BIN):
	curl -s https://get.nextflow.io | bash
	chmod +x $(NF_BIN)

nextflow-git:
	if [ ! -d "$(NF_DIR)" ]; then git clone https://github.com/nextflow-io/nextflow.git  "$(NF_DIR)"; fi
	cd "$(NF_DIR)"; git checkout && make compile && git restore .; cd ..

compile-all: nextflow compile

check:
	./gradlew check --warning-mode all

.PHONY: clean test test-all all pkg-test tower-test

test: clean compile check #coverage

test-nextflow: clean nextflow-git compile check #coverage

test-all: clean compile-all check #coverage

#
# Create packages
#

pkg-test: compile-all
	echo "$(TEST_URI)"
	$(NF_BIN) run ./main.nf -profile standard -plugins $(PROJECT) --outdir "$(TEST_URI)"

pkg-fail: compile
	echo "$(TEST_URI)"
	$(NF_BIN) run ./fail.nf -profile standard -plugins $(PROJECT) --outdir "$(TEST_URI)"

tower-test: $(NF_BIN)
	$(NF_BIN) run "https://github.com/quiltdata/nf-quilt" -name local_einstein  -with-tower -r main -latest --pub "$(TEST_URI)"

# use `make $(PIPELINE) WRITE_BUCKET=my-s3-bucket` to publish `--outdir` to a Quilt package

$(PIPELINE): $(NF_BIN) install
	echo "Ensure you have docker running"
	$(NF_BIN) pull nf-core/$(PIPELINE)
	$(NF_BIN) run nf-core/$(PIPELINE) -profile test,docker -plugins $(PROJECT)@$(VERSION) --outdir "$(QUILT_URI)"

#
# Show dependencies
#

deps:
	./gradlew -q ${mm}dependencies

#
# Refresh SNAPSHOTs dependencies
#
refresh:
	./gradlew --refresh-dependencies dependencies

install: compile
	./gradlew copyPluginZip
	rm -rf ${HOME}/.nextflow/plugins/$(PROJECT)-${VERSION}
	cp -r build/plugins/$(PROJECT)-${VERSION} ${HOME}/.nextflow/plugins/
#
# Upload JAR artifacts to Maven Central

publish:
	echo "Ensure you have set 'github_organization=<owner>' in gradle.properties"
	ls gradle.properties # create locally or globally if it does not exist
	./gradlew :plugins:$(PROJECT):upload
	./gradlew :plugins:publishIndex
