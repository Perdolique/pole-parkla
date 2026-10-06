import { readFile, writeFile } from "node:fs/promises";
import { basename } from "node:path";

interface FixtureAsset {
  id: number;
  name: string;
  size: number;
  state: string;
  content: string;
}
interface FixtureRelease {
  id: number;
  tag_name: string;
  draft: boolean;
  prerelease: boolean;
  body: string | null;
  assets: FixtureAsset[];
}
export interface FixtureState {
  sha: string;
  releases: FixtureRelease[];
  events: string[];
}

const path = process.env.PP_TEST_GITHUB_STATE!;
const raw = await readFile(path, "utf8");
const state: FixtureState = JSON.parse(raw);
const args = process.argv.slice(2);
if (args[0] === "git") {
  if (args[1] === "tag") process.stdout.write("v1.0.0\nv1.0.1\n");
  else if (args[2] === "v1.0.0^{commit}") process.stdout.write("2ba03d59d23ceac77fca4836eaa7835537f27cf5");
  else process.stdout.write(state.sha);
} else if (args[0] === "api") {
  const endpoint = args.find((arg) => arg.startsWith("repos/"))!;
  const methodIndex = args.indexOf("--method");
  const method = methodIndex < 0 ? "GET" : args[methodIndex + 1];
  if (method === "DELETE") {
    const id = Number(endpoint.split("/").at(-1));
    state.events.push(`delete ${id}`);
    for (const release of state.releases) release.assets = release.assets.filter((asset) => asset.id !== id);
  } else if (method === "PATCH") {
    const id = Number(endpoint.split("/").at(-1));
    state.events.push(`publish ${id}`);
    state.releases.find((release) => release.id === id)!.draft = false;
  } else if (endpoint.includes("/commits/")) {
    process.stdout.write(state.sha);
  } else if (endpoint.includes("/releases/assets/")) {
    const id = Number(endpoint.split("/").at(-1));
    const asset = state.releases.flatMap((release) => release.assets).find((item) => item.id === id)!;
    process.stdout.write(asset.content);
  } else {
    process.stdout.write(JSON.stringify([state.releases]));
  }
} else if (args[0] === "release" && args[1] === "upload") {
  const release = state.releases.find((item) => item.tag_name === args[2])!;
  const paths = args.slice(3, args.indexOf("--repo"));
  state.events.push(`upload ${paths.length}`);
  for (const assetPath of paths) {
    const content = await readFile(assetPath, "utf8");
    release.assets.push({ id: 100 + release.assets.length, name: basename(assetPath), size: content.length, state: "uploaded", content });
  }
} else if (args[0] === "release" && args[1] === "create") {
  const content = await readFile(args[args.indexOf("--notes-file") + 1], "utf8");
  state.events.push(`create ${args[2]}`);
  state.releases.push({ id: 10, tag_name: args[2], draft: true, prerelease: false, body: content, assets: [] });
} else {
  throw new Error(`Unexpected gh call: ${args.join(" ")}`);
}
await writeFile(path, JSON.stringify(state));
