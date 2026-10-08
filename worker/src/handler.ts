import { createHash, timingSafeEqual } from 'node:crypto'

import {
  MAX_IMAGE_BYTES,
  MAX_MULTIPART_BYTES,
  validateRecognitionPayload,
  type ErrorCode,
  type ErrorResponse,
  type ProviderName,
  type RecognitionResponse
} from './contracts'

import { ProviderFailure, type ProviderServices } from './providers'

class RequestFailure extends Error {
  constructor(
    readonly code: ErrorCode,
    readonly status: number,
    message: string
  ) {
    super(message)

    this.name = 'RequestFailure'
  }
}

export async function handleRecognitionRequest(
  request: Request,
  expectedToken: string,
  providers: ProviderServices
): Promise<Response> {
  const requestId = crypto.randomUUID()
  let provider: ProviderName | null = null

  try {
    const providedToken = bearerToken(request.headers.get('Authorization'))

    if (!(await tokensMatch(providedToken, expectedToken))) {
      throw new RequestFailure('UNAUTHORIZED', 401, 'Authorization failed')
    }

    const input = await parseMultipartInput(request)

    provider = input.provider

    const providerResult = await providers.recognize(input.provider, input.image)
    const payload = validateRecognitionPayload(providerResult.payload)

    if (payload === null) {
      throw new ProviderFailure(
        'INVALID_PROVIDER_RESPONSE',
        502,
        providerResult.upstreamRequestId,
        'Provider returned invalid recognition data'
      )
    }

    const response: RecognitionResponse = {
      requestId,
      provider: input.provider,
      plateCandidates: payload.plateCandidates,
      vehicleMake: payload.vehicleMake,
      vehicleModel: payload.vehicleModel,
      suggestedViolationType: payload.suggestedViolationType
    }

    return json(response, 200)
  } catch (error) {
    const failure = toFailure(error)

    logFailure(requestId, provider, failure, error)

    const response: ErrorResponse = {
      requestId,
      code: failure.code,
      message: safeMessage(failure.code)
    }

    return json(response, failure.status)
  }
}

interface RecognitionInput {
  provider: ProviderName;
  image: File;
}

async function parseMultipartInput(request: Request): Promise<RecognitionInput> {
  const contentType = request.headers.get('Content-Type') ?? ''

  if (!contentType.toLowerCase().startsWith('multipart/form-data;')) {
    throw new RequestFailure('INVALID_IMAGE', 400, 'Expected multipart form data')
  }

  const contentLength = Number(request.headers.get('Content-Length'))

  if (Number.isFinite(contentLength) && contentLength > MAX_MULTIPART_BYTES) {
    throw new RequestFailure('PAYLOAD_TOO_LARGE', 413, 'Multipart body is too large')
  }

  const body = await readBoundedBody(request, MAX_MULTIPART_BYTES)
  let formData: FormData

  try {
    const boundedRequest = new Request(request.url, {
      method: 'POST',
      headers: { 'Content-Type': contentType },
      body
    })

    formData = await boundedRequest.formData()
  } catch (error) {
    throw new RequestFailure('INVALID_IMAGE', 400, errorMessage(error))
  }

  const keys = new Set(formData.keys())

  if ([...keys].some((key) => key !== 'provider' && key !== 'image')) {
    throw new RequestFailure('INVALID_IMAGE', 400, 'Unexpected multipart field')
  }

  if (formData.getAll('provider').length !== 1 || formData.getAll('image').length !== 1) {
    throw new RequestFailure('INVALID_IMAGE', 400, 'Expected one provider and one image')
  }

  const providerValue = formData.get('provider')

  if (providerValue !== 'workers_ai' && providerValue !== 'openai') {
    throw new RequestFailure('PROVIDER_UNAVAILABLE', 400, 'Unsupported provider')
  }

  const image = formData.get('image')

  if (!(image instanceof File) || image.type.toLowerCase() !== 'image/jpeg') {
    throw new RequestFailure('INVALID_IMAGE', 400, 'Expected a JPEG image')
  }

  if (image.size === 0) {
    throw new RequestFailure('INVALID_IMAGE', 400, 'Image is empty')
  }

  if (image.size > MAX_IMAGE_BYTES) {
    throw new RequestFailure('PAYLOAD_TOO_LARGE', 413, 'Image is too large')
  }

  const signature = new Uint8Array(await image.slice(0, 3).arrayBuffer())

  if (signature[0] !== 0xff || signature[1] !== 0xd8 || signature[2] !== 0xff) {
    throw new RequestFailure('INVALID_IMAGE', 400, 'JPEG signature is invalid')
  }

  return {
    provider: providerValue,
    image
  }
}

async function readBoundedBody(request: Request, maxBytes: number): Promise<Uint8Array> {
  if (request.body === null) {
    throw new RequestFailure('INVALID_IMAGE', 400, 'Request body is empty')
  }

  const reader = request.body.getReader()
  const chunks: Uint8Array[] = []
  let totalBytes = 0

  while (true) {
    const result = await reader.read()

    if (result.done) break

    totalBytes += result.value.byteLength

    if (totalBytes > maxBytes) {
      await reader.cancel()

      throw new RequestFailure('PAYLOAD_TOO_LARGE', 413, 'Multipart body is too large')
    }

    chunks.push(result.value)
  }

  const body = new Uint8Array(totalBytes)
  let offset = 0

  for (const chunk of chunks) {
    body.set(chunk, offset)

    offset += chunk.byteLength
  }

  return body
}

function bearerToken(authorization: string | null): string {
  if (authorization === null || !authorization.startsWith('Bearer ')) return ''

  return authorization.slice('Bearer '.length)
}

async function tokensMatch(provided: string, expected: string): Promise<boolean> {
  const providedHash = createHash('sha256').update(provided).digest()
  const expectedHash = createHash('sha256').update(expected).digest()

  return timingSafeEqual(providedHash, expectedHash) && expected.length > 0
}

interface FailureDetails {
  code: ErrorCode;
  status: number;
  upstreamRequestId: string | null;
}

function toFailure(error: unknown): FailureDetails {
  if (error instanceof RequestFailure || error instanceof ProviderFailure) {
    return {
      code: error.code,
      status: error.status,
      upstreamRequestId: error instanceof ProviderFailure ? error.upstreamRequestId : null
    }
  }

  return {
    code: 'PROVIDER_UNAVAILABLE',
    status: 503,
    upstreamRequestId: null
  }
}

function logFailure(
  requestId: string,
  provider: ProviderName | null,
  failure: FailureDetails,
  error: unknown
): void {
  const exception = error instanceof Error ? `${error.name}: ${error.message}` : String(error)
  const stack = error instanceof Error ? error.stack : null

  console.error(
    JSON.stringify({
      event: 'recognition_failed',
      requestId,
      provider,
      status: failure.status,
      upstreamRequestId: failure.upstreamRequestId,
      exception,
      stack
    })
  )
}

function safeMessage(code: ErrorCode): string {
  switch (code) {
    case 'UNAUTHORIZED':
      return 'Authorization failed.'
    case 'INVALID_IMAGE':
      return 'A valid JPEG image is required.'
    case 'PAYLOAD_TOO_LARGE':
      return 'The image is too large.'
    case 'RATE_LIMITED':
      return 'The provider is busy. Try again later.'
    case 'INVALID_PROVIDER_RESPONSE':
      return 'The provider returned an invalid response.'
    case 'PROVIDER_UNAVAILABLE':
      return 'The selected provider is unavailable.'
  }
}

function json(value: object, status: number): Response {
  return Response.json(value, {
    status,

    headers: {
      'Cache-Control': 'no-store',
      'Content-Type': 'application/json; charset=utf-8'
    }
  })
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}
