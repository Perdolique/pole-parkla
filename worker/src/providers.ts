import { Buffer } from "node:buffer"
import {
  parseModelJson,
  recognitionSchema,
  validateRecognitionPayload,
  type ErrorCode,
  type ProviderName,
  type RecognitionPayload,
} from "./contracts"

const MODEL_PROMPT = `Analyze the vehicle in this image and return only the requested JSON.
Read up to five possible registration numbers without inventing hidden characters.
Identify the vehicle make and model only when visible.
Suggest CYCLE_PATH or PEDESTRIAN_PATH only when the obstruction is visually clear; otherwise use null.`
const PROVIDER_TIMEOUT_MILLIS = 30_000

export interface ProviderRecognition {
  payload: unknown
  upstreamRequestId: string | null
}

export interface ProviderServices {
  recognize(provider: ProviderName, image: File): Promise<ProviderRecognition>
}

type WorkersAiClient = Pick<Ai, "aiGatewayLogId" | "run">

export class ProviderFailure extends Error {
  constructor(
    readonly code: ErrorCode,
    readonly status: number,
    readonly upstreamRequestId: string | null,
    message: string,
    cause?: unknown,
  ) {
    super(message, { cause })
    this.name = "ProviderFailure"
  }
}

export function createProviderServices(env: Env): ProviderServices {
  return {
    async recognize(provider, image) {
      if (provider === "workers_ai") {
        return recognizeWithWorkersAI(
          env.AI,
          image,
          env.WORKERS_AI_MODEL,
          env.WORKERS_AI_GATEWAY_ID,
        )
      }
      return recognizeWithOpenAI(
        image,
        {
          gatewayUrl: env.OPENAI_GATEWAY_URL,
          gatewayToken: env.OPENAI_GATEWAY_TOKEN,
          model: env.OPENAI_MODEL,
        },
        fetch,
      )
    },
  }
}

export async function recognizeWithWorkersAI(
  ai: WorkersAiClient,
  image: File,
  model: string,
  gatewayId: string,
): Promise<ProviderRecognition> {
  const imageUrl = await imageDataUrl(image)
  const input = {
    messages: [
      {
        role: "system",
        content: "Extract vehicle details. Never include commentary outside the JSON response.",
      },
      {
        role: "user",
        content: [
          { type: "text", text: MODEL_PROMPT },
          { type: "image_url", image_url: { url: imageUrl, detail: "high" } },
        ],
      },
    ],
    max_completion_tokens: 400,
    temperature: 0.1,
    response_format: {
      type: "json_schema",
      json_schema: {
        name: "pole_parkla_vehicle_recognition",
        strict: true,
        schema: recognitionSchema,
      },
    },
  }
  const options = workersAiOptions(gatewayId, AbortSignal.timeout(PROVIDER_TIMEOUT_MILLIS))

  try {
    const response = await ai.run(model, input, options)
    const upstreamRequestId = ai.aiGatewayLogId
    try {
      const raw = extractWorkersAiText(response)
      return {
        payload: parseAndValidateModelText(raw),
        upstreamRequestId,
      }
    } catch (error) {
      if (error instanceof ProviderFailure) {
        throw new ProviderFailure(
          error.code,
          error.status,
          upstreamRequestId,
          error.message,
          error,
        )
      }
      throw error
    }
  } catch (error) {
    if (error instanceof ProviderFailure) throw error
    const rateLimited = error instanceof Error && /rate.?limit|429/i.test(error.message)
    throw new ProviderFailure(
      rateLimited ? "RATE_LIMITED" : "PROVIDER_UNAVAILABLE",
      rateLimited ? 429 : 503,
      ai.aiGatewayLogId,
      "Workers AI request failed",
      error,
    )
  }
}

interface OpenAIConfig {
  gatewayUrl: string
  gatewayToken: string
  model: string
}

type Fetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>

export async function recognizeWithOpenAI(
  image: File,
  config: OpenAIConfig,
  fetcher: Fetcher,
): Promise<ProviderRecognition> {
  const endpoint = openAiEndpoint(config.gatewayUrl)
  const imageUrl = await imageDataUrl(image)
  const requestBody = {
    model: config.model,
    store: false,
    reasoning: { effort: "none" },
    max_output_tokens: 400,
    input: [
      {
        role: "user",
        content: [
          { type: "input_text", text: MODEL_PROMPT },
          { type: "input_image", image_url: imageUrl, detail: "high" },
        ],
      },
    ],
    text: {
      format: {
        type: "json_schema",
        name: "pole_parkla_vehicle_recognition",
        strict: true,
        schema: recognitionSchema,
      },
    },
  }

  let response: Response
  try {
    response = await fetcher(endpoint, {
      method: "POST",
      headers: {
        "cf-aig-authorization": `Bearer ${config.gatewayToken}`,
        "cf-aig-collect-log": "false",
        "cf-aig-collect-log-payload": "false",
        "cf-aig-skip-cache": "true",
        "cf-aig-request-timeout": String(PROVIDER_TIMEOUT_MILLIS),
        "cf-aig-max-attempts": "1",
        "Content-Type": "application/json",
      },
      body: JSON.stringify(requestBody),
      signal: AbortSignal.timeout(PROVIDER_TIMEOUT_MILLIS),
    })
  } catch (error) {
    throw new ProviderFailure(
      "PROVIDER_UNAVAILABLE",
      503,
      null,
      "OpenAI request failed",
      error,
    )
  }

  const upstreamRequestId = response.headers.get("x-request-id") ?? response.headers.get("cf-ray")
  if (!response.ok) {
    const rateLimited = response.status === 429
    throw new ProviderFailure(
      rateLimited ? "RATE_LIMITED" : "PROVIDER_UNAVAILABLE",
      rateLimited ? 429 : 503,
      upstreamRequestId,
      `OpenAI returned HTTP ${response.status}`,
    )
  }

  let responseBody: unknown
  try {
    responseBody = await response.json()
  } catch (error) {
    throw new ProviderFailure(
      "INVALID_PROVIDER_RESPONSE",
      502,
      upstreamRequestId,
      "OpenAI returned malformed JSON",
      error,
    )
  }
  try {
    const raw = extractOpenAIText(responseBody)
    return {
      payload: parseAndValidateModelText(raw),
      upstreamRequestId,
    }
  } catch (error) {
    if (error instanceof ProviderFailure) {
      throw new ProviderFailure(
        error.code,
        error.status,
        upstreamRequestId,
        error.message,
        error,
      )
    }
    throw error
  }
}

export function workersAiOptions(gatewayId: string, signal: AbortSignal): AiOptions {
  if (gatewayId.trim() === "") {
    return { signal }
  }
  return {
    signal,
    gateway: {
      id: gatewayId,
      collectLog: false,
      skipCache: true,
      requestTimeoutMs: PROVIDER_TIMEOUT_MILLIS,
      retries: { maxAttempts: 1 },
    },
  }
}

function parseAndValidateModelText(raw: string): RecognitionPayload {
  const parsed = parseModelJson(raw)
  const payload = validateRecognitionPayload(parsed)
  if (payload === null) {
    throw new ProviderFailure(
      "INVALID_PROVIDER_RESPONSE",
      502,
      null,
      "Provider returned invalid recognition JSON",
    )
  }
  return payload
}

function extractWorkersAiText(value: unknown): string {
  if (!isRecord(value) || !Array.isArray(value.choices)) {
    throw new ProviderFailure(
      "INVALID_PROVIDER_RESPONSE",
      502,
      null,
      "Workers AI response has no choices",
    )
  }
  const firstChoice = value.choices[0]
  if (!isRecord(firstChoice) || !isRecord(firstChoice.message)) {
    throw new ProviderFailure(
      "INVALID_PROVIDER_RESPONSE",
      502,
      null,
      "Workers AI response has no message",
    )
  }
  const content = firstChoice.message.content
  if (typeof content !== "string") {
    throw new ProviderFailure(
      "INVALID_PROVIDER_RESPONSE",
      502,
      null,
      "Workers AI response has no text",
    )
  }
  return content
}

function extractOpenAIText(value: unknown): string {
  if (!isRecord(value) || !Array.isArray(value.output)) {
    throw new ProviderFailure(
      "INVALID_PROVIDER_RESPONSE",
      502,
      null,
      "OpenAI response has no output",
    )
  }
  for (const item of value.output) {
    if (!isRecord(item) || !Array.isArray(item.content)) continue
    for (const content of item.content) {
      if (isRecord(content) && content.type === "output_text" && typeof content.text === "string") {
        return content.text
      }
    }
  }
  throw new ProviderFailure(
    "INVALID_PROVIDER_RESPONSE",
    502,
    null,
    "OpenAI response has no output text",
  )
}

async function imageDataUrl(image: File): Promise<string> {
  const imageBuffer = await image.arrayBuffer()
  const base64Image = Buffer.from(imageBuffer).toString("base64")
  return `data:image/jpeg;base64,${base64Image}`
}

function openAiEndpoint(rawGatewayUrl: string): string {
  const gatewayUrl = new URL(rawGatewayUrl)
  if (gatewayUrl.protocol !== "https:") {
    throw new ProviderFailure(
      "PROVIDER_UNAVAILABLE",
      503,
      null,
      "OpenAI gateway URL must use HTTPS",
    )
  }
  return `${gatewayUrl.toString().replace(/\/+$/, "")}/responses`
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
}
