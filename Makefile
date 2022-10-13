PROJECT := nf-quilt3
REPORT := ./plugins/$(PROJECT)/build/reports/tests/test/index.html
BUCKET := quilt-ernest-staging
PIPELINE := sarek
QUILT_URI :=  quilt+s3://$(BUCKET)#package=$(PROJECT)/$(PIPELINE)&path=.

verify: compile
	clear
	./gradlew check || open $(REPORT)

clean:
	./gradlew clean

compile:
	./gradlew compileGroovy
	./gradlew exportClasspath
	@echo "DONE `date`"

check:
	./gradlew check


$(PIPELINE): compile
	./launch.sh run nf-core/$(PIPELINE) -profile test,docker -plugins $(PROJECT) --outdir "$(QUILT_URI)"
#
# Show dependencies try `make deps config=runtime`, `make deps config=google`
#
deps:
	./gradlew -q ${mm}dependencies --configuration ${config}

deps-all:
	./gradlew -q dependencyInsight --configuration ${config} --dependency ${module}

#
# Refresh SNAPSHOTs dependencies
#
refresh:
	./gradlew --refresh-dependencies

#
# Run all tests or selected ones
#
test:
ifndef class
	./gradlew ${mm}test
else
	./gradlew ${mm}test --tests ${class}
endif

fast:
	./gradlew ${mm}test --fail-fast || open ./plugins/nf-quilt3/build/reports/tests/test/index.html

#
# Upload JAR artifacts to Maven Central
#
upload:
	./gradlew upload

upload-plugins:
	./gradlew plugins:upload

publish-index:
	./gradlew plugins:publishIndex
