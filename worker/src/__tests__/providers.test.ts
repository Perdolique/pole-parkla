import { afterEach, describe, expect, test, vi } from "vitest"
import { recognizeWithOpenAI, recognizeWithWorkersAI, workersAiOptions } from "../providers"

const EXPECTED_IMAGE_DATA_URL = "data:image/jpeg;base64,/9j/2Q=="

interface OpenAIRequestBody {
  store: boolean
  model: string
  input: Array<{
    content: unknown[]
  }>
  text: { format: { type: string; strict: boolean } }
}

interface WorkersAiInput {
  messages: Array<{
    content: unknown
  }>
  max_completion_tokens: number
  temperature: number
}

function jpegFile(): File {
  return new File([new Uint8Array([0xff, 0xd8, 0xff, 0xd9])], "vehicle.jpg", {
    type: "image/jpeg",
  })
}

function openAiResponse(outputText: string, status = 200): Response {
  return Response.json(
    {
      output: [
        {
          type: "message",
          content: [{ type: "output_text", text: outputText }],
        },
      ],
    },
    { status, headers: { "x-request-id": "openai-request" } },
  )
}

describe(recognizeWithOpenAI, () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  test("uses Responses API with store false, structured output and payload logging disabled", async () => {
    const fetcher = vi.fn().mockResolvedValue(
      openAiResponse(
        JSON.stringify({
          plateCandidates: ["123 ABC"],
          vehicleMake: null,
          vehicleModel: null,
          suggestedViolationType: null,
        }),
      ),
    )

    const result = await recognizeWithOpenAI(
      jpegFile(),
      {
        gatewayUrl: "https://gateway.ai.cloudflare.com/v1/account/gateway/openai",
        gatewayToken: "gateway-token",
        model: "gpt-5.4-mini",
      },
      fetcher,
    )

    expect(result.upstreamRequestId).toBe("openai-request")
    expect(fetcher).toHaveBeenCalledTimes(1)
    const [url, init] = fetcher.mock.calls[0] as [string, RequestInit]
    expect(url).toBe("https://gateway.ai.cloudflare.com/v1/account/gateway/openai/responses")
    expect(init.headers).toMatchObject({
      "cf-aig-authorization": "Bearer gateway-token",
      "cf-aig-collect-log": "false",
      "cf-aig-collect-log-payload": "false",
      "cf-aig-request-timeout": "30000",
    })
    const requestBody = JSON.parse(String(init.body)) as OpenAIRequestBody
    expect(requestBody.store).toBe(false)
    expect(requestBody.model).toBe("gpt-5.4-mini")
    expect(requestBody.text.format).toMatchObject({ type: "json_schema", strict: true })
    expect(requestBody.input[0]?.content).toContainEqual({
      type: "input_image",
      image_url: EXPECTED_IMAGE_DATA_URL,
      detail: "high",
    })
    expect(JSON.stringify(requestBody)).not.toContain("coordinates")
    expect(JSON.stringify(requestBody)).not.toContain("email")
    expect(JSON.stringify(requestBody)).not.toContain("phone")
  })

  test("rejects malformed provider JSON", async () => {
    const fetcher = vi.fn().mockResolvedValue(openAiResponse("not json"))

    await expect(
      recognizeWithOpenAI(
        jpegFile(),
        {
          gatewayUrl: "https://gateway.ai.cloudflare.com/v1/account/gateway/openai",
          gatewayToken: "gateway-token",
          model: "gpt-5.4-mini",
        },
        fetcher,
      ),
    ).rejects.toMatchObject({
      code: "INVALID_PROVIDER_RESPONSE",
    })
  })
})

describe(recognizeWithWorkersAI, () => {
  test("uses the configured vision model and validates the default binding response", async () => {
    const run = vi.fn().mockResolvedValue({
      choices: [
        {
          message: {
            content: JSON.stringify({
              plateCandidates: ["123 ABC"],
              vehicleMake: "Toyota",
              vehicleModel: "Corolla",
              suggestedViolationType: null,
            }),
          },
        },
      ],
    })
    const ai = {
      aiGatewayLogId: "workers-ai-request",
      run: run as Ai["run"],
    } satisfies Pick<Ai, "aiGatewayLogId" | "run">

    const result = await recognizeWithWorkersAI(
      ai,
      jpegFile(),
      "@cf/google/gemma-4-26b-a4b-it",
      "",
    )

    expect(result).toEqual({
      payload: {
        plateCandidates: ["123 ABC"],
        vehicleMake: "Toyota",
        vehicleModel: "Corolla",
        suggestedViolationType: null,
      },
      upstreamRequestId: "workers-ai-request",
    })
    expect(run).toHaveBeenCalledTimes(1)
    const [model, input, options] = run.mock.calls[0] as [string, WorkersAiInput, AiOptions]
    expect(model).toBe("@cf/google/gemma-4-26b-a4b-it")
    expect(input).toMatchObject({ max_completion_tokens: 400, temperature: 0.1 })
    expect(input.messages[1]?.content).toEqual(
      expect.arrayContaining([
        {
          type: "image_url",
          image_url: { url: EXPECTED_IMAGE_DATA_URL, detail: "high" },
        },
      ]),
    )
    expect(options.gateway).toBeUndefined()
    expect(options.signal).toBeInstanceOf(AbortSignal)
  })
})

describe(workersAiOptions, () => {
  test("disables AI Gateway logging and caching for Workers AI", () => {
    const options = workersAiOptions("pole-parkla", AbortSignal.timeout(1_000))

    expect(options.gateway).toMatchObject({
      id: "pole-parkla",
      collectLog: false,
      skipCache: true,
      retries: { maxAttempts: 1 },
      requestTimeoutMs: 30_000,
    })
  })

  test("does not create a gateway configuration when no gateway is configured", () => {
    const options = workersAiOptions("", AbortSignal.timeout(1_000))

    expect(options.gateway).toBeUndefined()
  })
})
