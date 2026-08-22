export const MAX_IMAGE_BYTES = 1 * 1024 * 1024
export const MAX_MULTIPART_BYTES = MAX_IMAGE_BYTES + 64 * 1024

export const recognitionSchema = {
  type: "object",
  additionalProperties: false,
  properties: {
    plateCandidates: {
      type: "array",
      maxItems: 5,
      items: { type: "string", minLength: 1, maxLength: 20 },
    },
    vehicleMake: { type: ["string", "null"], maxLength: 80 },
    vehicleModel: { type: ["string", "null"], maxLength: 80 },
    suggestedViolationType: {
      type: ["string", "null"],
      enum: ["CYCLE_PATH", "PEDESTRIAN_PATH", null],
    },
  },
  required: [
    "plateCandidates",
    "vehicleMake",
    "vehicleModel",
    "suggestedViolationType",
  ],
} as const

export type ProviderName = "workers_ai" | "openai"
export type SuggestedViolationType = "CYCLE_PATH" | "PEDESTRIAN_PATH"

export interface RecognitionPayload {
  plateCandidates: string[]
  vehicleMake: string | null
  vehicleModel: string | null
  suggestedViolationType: SuggestedViolationType | null
}

export interface RecognitionResponse extends RecognitionPayload {
  requestId: string
  provider: ProviderName
}

export type ErrorCode =
  | "UNAUTHORIZED"
  | "INVALID_IMAGE"
  | "PAYLOAD_TOO_LARGE"
  | "RATE_LIMITED"
  | "PROVIDER_UNAVAILABLE"
  | "INVALID_PROVIDER_RESPONSE"

export interface ErrorResponse {
  code: ErrorCode
  message: string
  requestId: string
}

export function validateRecognitionPayload(value: unknown): RecognitionPayload | null {
  if (!isRecord(value)) return null
  const candidates = value.plateCandidates
  if (!Array.isArray(candidates) || candidates.length > 5) return null
  if (!candidates.every((candidate) => typeof candidate === "string")) return null

  const normalizedCandidates: string[] = []
  for (const candidate of candidates) {
    const normalized = normalizePlate(candidate)
    if (normalized === null) return null
    if (!normalizedCandidates.includes(normalized)) normalizedCandidates.push(normalized)
  }
  const vehicleMake = nullableBoundedString(value.vehicleMake, 80)
  const vehicleModel = nullableBoundedString(value.vehicleModel, 80)
  if (vehicleMake === undefined || vehicleModel === undefined) return null
  const suggestedViolationType = value.suggestedViolationType
  if (
    suggestedViolationType !== null &&
    suggestedViolationType !== "CYCLE_PATH" &&
    suggestedViolationType !== "PEDESTRIAN_PATH"
  ) {
    return null
  }

  return {
    plateCandidates: normalizedCandidates,
    vehicleMake,
    vehicleModel,
    suggestedViolationType,
  }
}

export function parseModelJson(raw: string): unknown {
  const trimmed = raw.trim()
  const start = trimmed.indexOf("{")
  const end = trimmed.lastIndexOf("}")
  if (start < 0 || end <= start) return null

  try {
    return JSON.parse(trimmed.slice(start, end + 1))
  } catch {
    return null
  }
}

function normalizePlate(raw: string): string | null {
  const normalized = raw
    .trim()
    .toUpperCase()
    .replace(/[\s-]+/g, " ")
  if (normalized.length < 2 || normalized.length > 20) return null
  if (!/[A-Z0-9]/.test(normalized)) return null
  if (!/^[A-Z0-9 ]+$/.test(normalized)) return null
  return normalized
}

function nullableBoundedString(value: unknown, maxLength: number): string | null | undefined {
  if (value === null) return null
  if (typeof value !== "string") return
  const trimmed = value.trim()
  if (trimmed.length === 0) return null
  if (trimmed.length > maxLength) return
  return trimmed
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
}
