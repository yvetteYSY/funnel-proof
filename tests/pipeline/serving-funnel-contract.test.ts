import { readFile } from "node:fs/promises";
import path from "node:path";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";
import { describe, expect, it } from "vitest";

const contractPath = path.resolve("pipeline/contracts/serving-funnel-daily.v1.schema.json");
const contract = JSON.parse(await readFile(contractPath, "utf8"));
const ajv = new Ajv2020({ allErrors: true, strict: true });
addFormats(ajv);
const validate = ajv.compile(contract);

const aggregateRow = {
  workspace_id: "demo_workspace",
  event_date: "2026-08-18",
  funnel_definition_version: "1.0.0",
  stage_name: "activation_completed",
  unique_users: 7,
  snapshot_version: "hourly-2026-08-18t16",
  canonical_completed_at: "2026-08-18T16:00:00Z",
  data_sla_status: "healthy"
};

describe("canonical serving funnel contract", () => {
  it("accepts a tenant-scoped aggregate snapshot row", () => {
    expect(validate(aggregateRow)).toBe(true);
  });

  it("rejects raw identity fields from the ClickHouse serving contract", () => {
    expect(validate({ ...aggregateRow, anonymous_id: "must-never-be-served" })).toBe(false);
  });
});
