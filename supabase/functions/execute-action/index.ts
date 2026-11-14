import { productionDeps } from "../_shared/env.ts";
import { serve } from "../_shared/http.ts";
import { executeActionHandler } from "./handler.ts";

Deno.serve(serve(executeActionHandler(productionDeps())));
