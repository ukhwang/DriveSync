import { productionDeps } from "../_shared/env.ts";
import { serve } from "../_shared/http.ts";
import { insightHandler } from "./handler.ts";

Deno.serve(serve(insightHandler(productionDeps())));
