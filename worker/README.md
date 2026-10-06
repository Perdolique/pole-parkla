# Pole parkla! recognition Worker

Repository-owned Worker code, tests, configuration, and documentation are licensed
under the GNU Affero General Public License, version 3 only (`AGPL-3.0-only`). See
[LICENSE](LICENSE) and the repository [licensing scope](../LICENSING.md).

This stateless Cloudflare Worker exposes one authenticated endpoint for optional recognition of the Android app's primary vehicle photo. It has no D1, KV, R2 or queue bindings.

```http
POST /v1/recognize
Authorization: Bearer <app-token>
Content-Type: multipart/form-data

provider = workers_ai | openai
image = <JPEG>
```

Only `provider` and one JPEG are accepted. The JPEG must be non-empty, have a valid JPEG signature and be no larger than 1 MB. Any additional multipart field is rejected, preventing profile, contact, address, coordinate or letter data from entering this API accidentally.

## Prerequisites

- a Cloudflare account with Workers AI access;
- Node.js and pnpm;
- Wrangler authentication;
- optionally, an OpenAI key configured as Cloudflare AI Gateway BYOK.

Install and verify:

```sh
pnpm install
pnpm run check
pnpm run cf-typegen
pnpm run deploy:dry
```

## Configuration

Edit `wrangler.jsonc`:

- keep the `AI` binding for Workers AI;
- set `WORKERS_AI_GATEWAY_ID` to an AI Gateway ID, or leave it empty to call the binding without a gateway;
- set `WORKERS_AI_MODEL` to the desired vision model;
- replace the account and gateway placeholders in `OPENAI_GATEWAY_URL`;
- change `OPENAI_MODEL` centrally when needed without releasing a new APK.

The default model values are:

```text
WORKERS_AI_MODEL=@cf/google/gemma-4-26b-a4b-it
OPENAI_MODEL=gpt-5.4-mini
```

Create a long random app token and store it as a Worker secret. Do not put it in `wrangler.jsonc`:

```sh
pnpm exec wrangler secret put APP_BEARER_TOKEN
```

For OpenAI, add the OpenAI API key to AI Gateway's BYOK configuration. Create an authenticated AI Gateway token that is allowed to use that gateway, then store that gateway token as a second Worker secret:

```sh
pnpm exec wrangler secret put OPENAI_GATEWAY_TOKEN
```

The OpenAI API key therefore stays in Cloudflare's gateway configuration. It is not present in the Worker source, Wrangler variables or Android APK.

For local development, copy `.dev.vars.example` to the ignored `.dev.vars` and replace its placeholders. Never commit `.dev.vars`.

## Deploy and smoke test

Deploy after the checks pass:

```sh
pnpm exec wrangler deploy
```

Configure the deployed HTTPS URL and the same app bearer token in Pole parkla settings. Cloud calls are deliberately user-triggered; normal CI uses provider mocks and makes no paid AI request. Run a separate manual smoke test for each configured provider with a non-sensitive test image.

## Response contract

A successful result is validated and normalized before it leaves the Worker:

```json
{
  "requestId": "uuid",
  "provider": "workers_ai",
  "plateCandidates": ["123 ABC"],
  "vehicleMake": "Toyota",
  "vehicleModel": "Corolla",
  "suggestedViolationType": "CYCLE_PATH"
}
```

`vehicleMake`, `vehicleModel` and `suggestedViolationType` can be `null`. `plateCandidates` contains at most five normalized values. Free-form or malformed model output is rejected.

Stable error codes are:

- `UNAUTHORIZED`
- `INVALID_IMAGE`
- `PAYLOAD_TOO_LARGE`
- `RATE_LIMITED`
- `PROVIDER_UNAVAILABLE`
- `INVALID_PROVIDER_RESPONSE`

Every response includes a Worker `requestId`. Custom technical failure logs contain the provider, HTTP status, upstream request ID, exception and stack. They do not include the image, registration number or model prompt. Automatic invocation logs and traces are disabled in `wrangler.jsonc` so successful multipart requests do not create request metadata logs.

## Provider privacy controls

- Workers AI calls disable AI Gateway collection when a gateway ID is configured (`collectLog: false`) and disable gateway cache/retries.
- OpenAI calls set `store: false`, use Structured Outputs, skip gateway cache/retries and send both `cf-aig-collect-log: false` and `cf-aig-collect-log-payload: false` so the request does not create an AI Gateway log entry.
- Both upstream calls have a 30-second deadline, leaving the Android client's 60-second timeout room to receive the Worker response.
- The Worker never retries through a different provider.
