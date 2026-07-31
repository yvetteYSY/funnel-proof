import { readFile, readdir } from "node:fs/promises";
import path from "node:path";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";

const root = new URL("..", import.meta.url).pathname;
const schemaPath = path.join(root, "contracts/events/v1/event-envelope.schema.json");
const fixturesPath = path.join(root, "tests/contracts");
const schema = JSON.parse(await readFile(schemaPath, "utf8"));
const ajv = new Ajv2020({ allErrors: true, strict: true });
addFormats(ajv);
const validate = ajv.compile(schema);
const fixtureNames = (await readdir(fixturesPath)).filter((name) => name.endsWith(".json"));

let failed = false;
for (const fixtureName of fixtureNames) {
  const fixture = JSON.parse(await readFile(path.join(fixturesPath, fixtureName), "utf8"));
  const valid = validate(fixture);
  const expectedValid = fixtureName.endsWith(".valid.json");

  if (valid !== expectedValid) {
    failed = true;
    console.error(`${fixtureName}: expected valid=${expectedValid}, got valid=${valid}`);
    console.error(validate.errors);
  }
}

if (failed) process.exitCode = 1;
else console.log(`Validated ${fixtureNames.length} contract fixtures.`);
