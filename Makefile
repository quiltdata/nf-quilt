sinclude .env # create from example.env
PROJECT ?= nf-quilt
WRITE_BUCKET ?= udp-spec
FRAGMENT ?= &path=.
NF_DIR ?= ../nextflow
NF_GIT ?= $(NF_DIR)/nextflow
NF_BIN ?= ./launch.sh
PID ?= $$$$
PIPELINE ?= sarek
QUERY ?= ?Name=$(USER)&Owner=Kevin+Moore&Date=2023-03-07&Type=CRISPR&Notebook+URL=http%3A%2F%2Fexample.com
VERSION ?= $(shell grep 'Plugin-Version' plugins/$(PROJECT)/src/resources/META-INF/MANIFEST.MF | awk '{ print $$2 }')
NXF_VER ?= $(shell cat VERSION)
TEST_URI ?= quilt+s3://$(WRITE_BUCKET)$(QUERY)\#package=nf-quilt/dest-$(VERSION)$(FRAGMENT)
PIPE_OUT ?=  quilt+s3://$(WRITE_BUCKET)\#package=$(PROJECT)/$(PIPELINE)
S3_BASE = s3://$(WRITE_BUCKET)/$(PROJECT)
REPORT ?= ./plugins/$(PROJECT)/build/reports/tests/test/index.html

verify: #compile
	echo $(WRITE_BUCKET)
	./gradlew test ${ONE} || open $(REPORT)

fast:
	./gradlew test ${ONE} --fail-fast || open $(REPORT)
# example: make fast ONE="--tests QuiltProductTest"
check-env:
	echo $(VERSION)
	echo $(WRITE_BUCKET)
	echo "$(TEST_URI)"
	echo "Use 'make WRITE_BUCKET=<value>' to override" 
	printenv MAKEFLAGS

clean:
	./gradlew clean
	rm -rf null results work
	rm -rf */*/build plugins/nf-quilt/bin
	rm -f .nextflow.log* .launch*classpath

compile:
	./gradlew compileGroovy exportClasspath
	@echo "DONE `date`"

nextflow:
	if [ ! -d "$(NF_DIR)" ]; then git clone https://github.com/nextflow-io/nextflow.git  "$(NF_DIR)"; fi
	cd "$(NF_DIR)"; git checkout && make compile && git restore .; cd ..

compile-all: nextflow compile

check:
	./gradlew check --warning-mode all

.PHONY: clean test test-all all pkg-test tower-test

test: clean compile check verifyCoverage

test-nextflow: clean nextflow-git compile check

test-all: clean compile-all check coverage

coverage:
	./gradlew jacocoTestReport
	open plugins/nf-quilt/build/reports/jacoco/test/html/index.html

verifyCoverage:
	./gradlew jacocoTestCoverageVerification

groovysh:
	./gradlew -q --no-daemon --console=plain --init-script groovysh-task.gradle groovysh

#
# Create packages
#

pkg-test: compile #-all
	echo "$(TEST_URI)"
	$(NF_BIN) run ./main.nf -profile standard -plugins $(PROJECT) --outdir "$(TEST_URI)"

dyn-test: compile #-all
	$(NF_BIN) run wf/main.dynamic.nf -profile standard -plugins $(PROJECT)

s3-overlay: compile
	$(NF_BIN) run ./main.nf --plugins $(PROJECT) --outdir "$(S3_BASE)/s3-overlay" --input "$(S3_BASE)/s3-in"  

s3-test: compile
	$(NF_BIN) run ./main.nf --outdir "$(S3_BASE)/s3-test" --input "$(S3_BASE)/s3-in"

s3-in: compile
	$(NF_BIN) run ./main.nf -profile standard -plugins $(PROJECT) --outdir "$(TEST_URI)" --input "$(S3_BASE)/s3-in"

s3-out: compile
	$(NF_BIN) run ./main.nf -profile standard -plugins $(PROJECT) --outdir "$(S3_BASE)/s3-out"

pkg-fail: compile
	echo "$(TEST_URI)"
	$(NF_BIN) run wf/fail.nf -profile standard -plugins $(PROJECT) --outdir "$(TEST_URI)"

path-input: compile
	mkdir -p work
	date > work/COPY_THIS.md
	echo "$(TEST_URI)"
	$(NF_BIN) run wf/main.path.nf -profile standard -plugins $(PROJECT) --outdir "./results"

tower-test: $(NF_BIN)
	$(NF_BIN) run "https://github.com/quiltdata/nf-quilt" -name local_einstein  -with-tower -r main -latest --pub "$(TEST_URI)"

# use `make $(PIPELINE) WRITE_BUCKET=my-s3-bucket` to publish `--outdir` to a Quilt package

$(PIPELINE): $(NF_BIN) install
	echo "Ensure you have docker running"
	$(NF_BIN) pull nf-core/$(PIPELINE)
	$(NF_BIN) run nf-core/$(PIPELINE) -profile test,docker -plugins $(PROJECT)@$(VERSION) --outdir "$(PIPE_OUT)"

fetchngs: $(NF_GIT)
	NXF_VER=$(NXF_VER) $(NF_GIT) run nf-core/fetchngs -profile test,docker --input wf/ids.csv --outdir "s3://$(WRITE_BUCKET)/nf-quilt/fetchngs"

nf-git-ver: $(NF_GIT)
	NXF_VER=$(NXF_VER) $(NF_GIT) -v

#
# Show dependencies
#

deps:
	./gradlew -q ${mm}dependencies

update:
	./gradlew useLatestVersions
	make check

refresh:
	./gradlew --refresh-dependencies dependencies

install: compile
	./gradlew copyPluginZip
	rm -rf ${HOME}/.nextflow/plugins/$(PROJECT)-${VERSION}
	cp -r build/plugins/$(PROJECT)-${VERSION} ${HOME}/.nextflow/plugins/
#
# Upload JAR artifacts to Maven Central
#

publish:
	echo "Ensure you have set 'github_organization=<owner>' in gradle.properties"
	ls gradle.properties # create locally or globally if it does not exist
	./gradlew :plugins:$(PROJECT):upload
	./gradlew :plugins:publishIndex
