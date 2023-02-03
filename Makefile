PROJECT := nf-quilt
REPORT := ./plugins/$(PROJECT)/build/reports/tests/test/index.html
BUCKET := quilt-ernest-staging
PIPELINE := sarek
QUILT_URI :=  quilt+s3://$(BUCKET)\#package=$(PROJECT)/$(PIPELINE)&path=.

verify: #compile
	./gradlew check || open $(REPORT)

clean:
	./gradlew clean
	rm -rf work
	rm -rf build
	rm -rf modules/*/build
	rm -rf plugins/*/build
	rm -f .nextflow.log*
	rm -f .launch.classpath

compile:
	./gradlew compileGroovy exportClasspath
	@echo "DONE `date`"

check:
	./gradlew check --warning-mode all

coverage: compile
	./gradlew cloverInstrumentCodeForTest

.PHONY: clean test all
test: clean compile check #coverage


#
# Create packages
#

# use 'make pkg-test BUCKET=my-s3-bucket' to publish test output to a Quilt package

pkg-test: compile
	./launch.sh run ./main.nf -profile standard -plugins $(PROJECT) --pub "quilt+s3://$(BUCKET)#package=test/hurdat"

# use `make $(PIPELINE) BUCKET=my-s3-bucket` to publish `--outdir` to a Quilt package

$(PIPELINE): compile
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
test:
	./gradlew ${mm}test

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
