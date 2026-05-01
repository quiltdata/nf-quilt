sinclude .env # create from example.env
export NPR_API_KEY WRITE_BUCKET
PROJECT ?= nf-quilt
WRITE_BUCKET ?= write-bucket-not-set
FRAGMENT ?= &path=.
QUERY ?= ?Name=$(USER)&Owner=Kevin+Moore&Date=2023-03-07&Type=CRISPR&Notebook+URL=http%3A%2F%2Fexample.com
VERSION ?= $(shell grep "^version" build.gradle | head -1 | awk -F"'" '{ print $$2 }')
NXF_VER ?= $(shell cat VERSION)
TEST_URI ?= quilt+s3://$(WRITE_BUCKET)$(QUERY)\#package=nf-quilt/dest-$(VERSION)$(FRAGMENT)
PIPELINE ?= sarek
PIPE_OUT ?= quilt+s3://$(WRITE_BUCKET)\#package=$(PROJECT)/$(PIPELINE)
S3_BASE = s3://$(WRITE_BUCKET)/$(PROJECT)
REPORT ?= ./build/reports/tests/test/index.html

.PHONY: all assemble clean test test-all check rebuild install package release verify fast \
        coverage verifyCoverage bump tag \
        check-env pkg-test dyn-test s3-overlay s3-test s3-in s3-out \
        pkg-fail path-input deps refresh

all: assemble

assemble:
	./gradlew assemble

clean:
	rm -rf .nextflow*
	rm -rf work results null
	rm -rf build
	./gradlew clean

test:
	./gradlew test

check:
	./gradlew check --warning-mode all

coverage:
	./gradlew jacocoTestReport
	open ./build/reports/jacoco/test/html/index.html

verifyCoverage:
	./gradlew jacocoTestCoverageVerification

verify:
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

rebuild:
	./gradlew clean build --refresh-dependencies

# make bump                 -> patch bump (default)
# make bump LEVEL=minor     -> minor bump
# make bump LEVEL=major     -> major bump
LEVEL ?= patch
bump:
	./wf/bump-version.sh $(LEVEL)

# Tag the current build.gradle version and create a GitHub release
# using the matching CHANGELOG section as release notes.
tag:
	./wf/tag-release.sh

test-all: clean test

install: assemble
	./gradlew installPlugin

package:
	./gradlew packagePlugin

release: tag
	./gradlew releasePlugin

#
# Create packages (real-S3 integration tests).
# These run the plugin via `nextflow` directly. Requires `nextflow` on PATH.
#

pkg-test: install
	echo "$(TEST_URI)"
	nextflow run ./main.nf -profile standard -plugins $(PROJECT)@$(VERSION) --outdir "$(TEST_URI)"

dyn-test: install
	nextflow run wf/main.dynamic.nf -profile standard -plugins $(PROJECT)@$(VERSION)

s3-overlay: install
	nextflow run ./main.nf --plugins $(PROJECT)@$(VERSION) --outdir "$(S3_BASE)/s3-overlay" --input "$(S3_BASE)/s3-in"

s3-test: install
	nextflow run ./main.nf -profile standard -plugins $(PROJECT)@$(VERSION) --outdir "$(S3_BASE)/s3-test" --input "$(S3_BASE)/s3-in"

s3-in: install
	nextflow run ./main.nf -profile standard -plugins $(PROJECT)@$(VERSION) --outdir "$(TEST_URI)" --input "$(S3_BASE)/s3-in"

s3-out: install
	nextflow run ./main.nf -profile standard -plugins $(PROJECT)@$(VERSION) --outdir "$(S3_BASE)/s3-out"

pkg-fail: install
	echo "$(TEST_URI)"
	nextflow run wf/fail.nf -profile standard -plugins $(PROJECT)@$(VERSION) --outdir "$(TEST_URI)"

path-input: install
	mkdir -p work
	date > work/COPY_THIS.md
	echo "$(TEST_URI)"
	nextflow run wf/main.path.nf -profile standard -plugins $(PROJECT)@$(VERSION) --outdir "./results"

#
# Show dependencies
#

deps:
	./gradlew -q dependencies

refresh:
	./gradlew --refresh-dependencies dependencies
