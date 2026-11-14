import { productionDeps } from "../_shared/env.ts";
import { serve } from "../_shared/http.ts";
import { briefingHandler } from "./handler.ts";

Deno.serve(serve(briefingHandler(productionDeps())));
