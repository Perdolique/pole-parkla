import { afterEach, beforeEach, describe, expect, test, vi } from "vitest"
import { MAX_IMAGE_BYTES } from "../contracts"
import { handleRecognitionRequest } from "../handler"
import { ProviderFailure, type ProviderServices } from "../providers"

const APP_TOKEN = "test-app-token"
const validPayload = {
  plateCandidates: ["003 PUK"],
  vehicleMake: "Toyota",
  vehicleModel: "Corolla",
  suggestedViolationType: "CYCLE_PATH",
} as const

function providerServices(result: unknown = validPayload): ProviderServices {
  return {
    recognize: vi.fn().mockResolvedValue({
      payload: result,
      upstreamRequestId: "upstream-1",
    }),
  }
}

function recognitionRequest(options: {
  provider?: string
  token?: string
  image?: File
  extraFields?: Record<string, string>
} = {}): Request {
  const form = new FormData()
  form.set("provider", options.provider ?? "workers_ai")
  form.set("image", options.image ?? jpegFile())
  for (const [key, value] of Object.entries(options.extraFields ?? {})) {
    form.set(key, value)
  }
  return new Request("https://worker.example/v1/recognize", {
    method: "POST",
    headers: { Authorization: `Bearer ${options.token ?? APP_TOKEN}` },
    body: form,
  })
}

function jpegFile(bytes: Uint8Array = new Uint8Array([0xff, 0xd8, 0xff, 0xd9])): File {
  return new File([bytes], "vehicle.jpg", { type: "image/jpeg" })
}

async function errorCode(response: Response): Promise<string> {
  const body = await response.json<{ code: string }>()
  return body.code
}

describe(handleRecognitionRequest, () => {
  beforeEach(() => {
    vi.spyOn(console, "error").mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  test("rejects a missing or invalid bearer token", async () => {
    const services = providerServices()

    const response = await handleRecognitionRequest(
      recognitionRequest({ token: "wrong" }),
      APP_TOKEN,
      services,
    )

    expect(response.status).toBe(401)
    expect(await errorCode(response)).toBe("UNAUTHORIZED")
    expect(services.recognize).not.toHaveBeenCalled()
  })

  test.each([
    ["image/png", new Uint8Array([0x89, 0x50, 0x4e, 0x47])],
    ["image/jpeg", new Uint8Array([0x00, 0x01, 0x02])],
  ])("rejects invalid image type or signature", async (mime, bytes) => {
    const image = new File([bytes], "vehicle", { type: mime })

    const response = await handleRecognitionRequest(
      recognitionRequest({ image }),
      APP_TOKEN,
      providerServices(),
    )

    expect(response.status).toBe(400)
    expect(await errorCode(response)).toBe("INVALID_IMAGE")
  })

  test("rejects an image larger than one megabyte", async () => {
    const bytes = new Uint8Array(MAX_IMAGE_BYTES + 1)
    bytes.set([0xff, 0xd8, 0xff])

    const response = await handleRecognitionRequest(
      recognitionRequest({ image: jpegFile(bytes) }),
      APP_TOKEN,
      providerServices(),
    )

    expect(response.status).toBe(413)
    expect(await errorCode(response)).toBe("PAYLOAD_TOO_LARGE")
  })

  test("validates the requested provider", async () => {
    const response = await handleRecognitionRequest(
      recognitionRequest({ provider: "automatic" }),
      APP_TOKEN,
      providerServices(),
    )

    expect(response.status).toBe(400)
    expect(await errorCode(response)).toBe("PROVIDER_UNAVAILABLE")
  })

  test("routes Workers AI and OpenAI separately without fallback", async () => {
    const services = providerServices()

    const workersResponse = await handleRecognitionRequest(
      recognitionRequest({ provider: "workers_ai" }),
      APP_TOKEN,
      services,
    )
    const openAiResponse = await handleRecognitionRequest(
      recognitionRequest({ provider: "openai" }),
      APP_TOKEN,
      services,
    )

    expect(workersResponse.status).toBe(200)
    expect(openAiResponse.status).toBe(200)
    expect(services.recognize).toHaveBeenNthCalledWith(1, "workers_ai", expect.any(File))
    expect(services.recognize).toHaveBeenNthCalledWith(2, "openai", expect.any(File))
    expect(services.recognize).toHaveBeenCalledTimes(2)
  })

  test("rejects contact and location fields instead of forwarding them", async () => {
    const services = providerServices()
    const response = await handleRecognitionRequest(
      recognitionRequest({
        extraFields: {
          email: "person@example.com",
          coordinates: "59.4,24.7",
        },
      }),
      APP_TOKEN,
      services,
    )

    expect(response.status).toBe(400)
    expect(await errorCode(response)).toBe("INVALID_IMAGE")
    expect(services.recognize).not.toHaveBeenCalled()
    const log = vi.mocked(console.error).mock.calls.flat().join(" ")
    expect(log).not.toContain("person@example.com")
    expect(log).not.toContain("59.4,24.7")
  })

  test("rejects malformed model output", async () => {
    const response = await handleRecognitionRequest(
      recognitionRequest(),
      APP_TOKEN,
      providerServices({ plateCandidates: "003 PUK" }),
    )

    expect(response.status).toBe(502)
    expect(await errorCode(response)).toBe("INVALID_PROVIDER_RESPONSE")
  })

  test.each([
    ["RATE_LIMITED", 429],
    ["PROVIDER_UNAVAILABLE", 503],
  ] as const)("maps upstream %s failures to stable errors", async (code, status) => {
    const services: ProviderServices = {
      recognize: vi.fn().mockRejectedValue(
        new ProviderFailure(code, status, "upstream-2", "raw upstream failure"),
      ),
    }

    const response = await handleRecognitionRequest(recognitionRequest(), APP_TOKEN, services)

    expect(response.status).toBe(status)
    expect(await errorCode(response)).toBe(code)
    const log = vi.mocked(console.error).mock.calls.flat().join(" ")
    expect(log).toContain("upstream-2")
    expect(log).toContain("raw upstream failure")
  })
})
