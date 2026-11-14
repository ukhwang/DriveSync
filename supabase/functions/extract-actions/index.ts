import { productionDeps } from "../_shared/env.ts";
import { serve } from "../_shared/http.ts";
import { extractActionsHandler } from "./handler.ts";

Deno.serve(serve(extractActionsHandler(productionDeps())));
