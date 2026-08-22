import { handleRecognitionRequest } from "./handler"
import { createProviderServices } from "./providers"

export default {
  async fetch(request, env): Promise<Response> {
    const url = new URL(request.url)
    if (url.pathname !== "/v1/recognize") return new Response("Not found", { status: 404 })
    if (request.method !== "POST") return new Response("Method not allowed", { status: 405 })

    return handleRecognitionRequest(
      request,
      env.APP_BEARER_TOKEN,
      createProviderServices(env),
    )
  },
} satisfies ExportedHandler<Env>
