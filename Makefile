.PHONY: doctor test verify collector-test

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

collector-test:
	cd services/collector && mvn -Dmaven.repo.local=../../.m2 test
