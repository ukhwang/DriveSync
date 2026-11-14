/**
 * Regenerates /schemas/*.schema.json from the Zod contracts (the single source of truth).
 * `--check` exits non-zero if the committed files are out of date (used by CI and tests).
 */
import { z } from "zod";
import { CONTRACTS } from "../_shared/schemas.ts";

const outDir = new URL("../../../schemas/", import.meta.url);

export function renderSchemas(): Record<string, string> {
  const files: Record<string, string> = {};
  for (const [name, schema] of Object.entries(CONTRACTS)) {
    const jsonSchema = {
      $id: `https://drivesync.app/schemas/${name}.schema.json`,
      ...z.toJSONSchema(schema, { target: "draft-2020-12" }),
    };
    files[`${name}.schema.json`] = JSON.stringify(jsonSchema, null, 2) + "\n";
  }
  return files;
}

if (import.meta.main) {
  const check = Deno.args.includes("--check");
  let stale = 0;
  for (const [file, content] of Object.entries(renderSchemas())) {
    const url = new URL(file, outDir);
    if (check) {
      const current = await Deno.readTextFile(url).catch(() => "");
      if (current !== content) {
        console.error(`out of date: schemas/${file}`);
        stale++;
      }
    } else {
      await Deno.writeTextFile(url, content);
      console.log(`wrote schemas/${file}`);
    }
  }
  if (stale > 0) Deno.exit(1);
}
