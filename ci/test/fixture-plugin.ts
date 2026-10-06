import { appendFile } from "node:fs/promises";
import type { PrepareContext, PublishContext } from "semantic-release";

export async function prepare(_config: unknown, context: PrepareContext): Promise<void> {
  await appendFile(context.env.PP_TEST_EVENTS, `prepare ${context.nextRelease.version}\n`);
}

export async function publish(_config: unknown, context: PublishContext) {
  await appendFile(context.env.PP_TEST_EVENTS, `publish ${context.nextRelease.version}\n`);
  return { name: context.nextRelease.version };
}
