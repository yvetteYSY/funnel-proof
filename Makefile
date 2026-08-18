.PHONY: doctor test verify collector-test flink-test flink-package spark-test airflow-test demo-start demo-stop demo-data demo-check gold-backfill

SCENARIO ?= healthy

doctor:
	@command -v node >/dev/null && node --version || echo "Missing: Node.js 22+"
	@command -v npm >/dev/null && npm --version || echo "Missing: npm"
	@command -v java >/dev/null && java --version || (command -v mvn >/dev/null && mvn --version | grep '^Java version:' || echo "Missing: Java 21+")
	@command -v mvn >/dev/null && mvn --version | head -n 1 || echo "Missing: Maven 3.9+"

test:
	npm test

verify:
	npm run verify
	cd services/collector && mvn -Dmaven.repo.local=../../.m2 test
	mvn -f pipeline/flink/pom.xml -Dmaven.repo.local=.m2 test
	mvn -f pipeline/spark/pom.xml -Dmaven.repo.local=.m2 test
	PYTHONPATH=pipeline/airflow python3 -m unittest discover -s pipeline/airflow/tests

collector-test:
	cd services/collector && mvn -Dmaven.repo.local=../../.m2 test

flink-test:
	mvn -f pipeline/flink/pom.xml -Dmaven.repo.local=.m2 test

flink-package:
	mvn -f pipeline/flink/pom.xml -Dmaven.repo.local=.m2 package

spark-test:
	mvn -f pipeline/spark/pom.xml -Dmaven.repo.local=.m2 test

airflow-test:
	PYTHONPATH=pipeline/airflow python3 -m unittest discover -s pipeline/airflow/tests

demo-start:
	bash scripts/demo-start.sh

demo-stop:
	bash scripts/demo-stop.sh

demo-data:
	node scripts/seed-demo-data.mjs $(SCENARIO)

demo-check:
	node scripts/demo-check.mjs

gold-backfill:
	mvn -f services/collector/pom.xml -Dmaven.repo.local=.m2 compile exec:java -Dexec.mainClass=dev.funnelproof.collector.LocalGoldBackfillApplication
