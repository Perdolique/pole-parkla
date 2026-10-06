import { execFile, spawn } from "node:child_process";
import { promisify } from "node:util";
import { fileURLToPath } from "node:url";
import type { Commit } from "semantic-release";

const rootUrl = new URL("../../", import.meta.url);
export const root = fileURLToPath(rootUrl);
const execFileAsync = promisify(execFile);

export async function capture(command: string, args: string[], cwd = root): Promise<string> {
  const result = await execFileAsync(command, args, { cwd, maxBuffer: 16 * 1024 * 1024 });
  return result.stdout.trim();
}

// Read the same unfiltered Git range for version policy and recovery notes.
export async function readCommits(from: string, to: string, cwd = root): Promise<Pick<Commit, "hash" | "message">[]> {
  const range = `${from}..${to}`;
  const rawLog = await capture("git", ["log", range, "--format=%H%x00%B%x00"], cwd);
  const parts = rawLog.split("\0");
  const commits = [];
  for (let i = 0; i + 1 < parts.length; i += 2) {
    const hash = parts[i].trim();
    const message = parts[i + 1].trim();
    commits.push({ hash, message });
  }
  return commits;
}

export async function run(command: string, args: string[], env = process.env): Promise<void> {
  const child = spawn(command, args, { cwd: root, env, stdio: "inherit" });
  await new Promise<void>((resolve, reject) => {
    child.once("error", reject);
    child.once("exit", (code, signal) => {
      if (code === 0) resolve();
      else reject(new Error(`${command} failed: exit ${code}, signal ${signal}`));
    });
  });
}

export async function isAncestor(older: string, newer: string): Promise<boolean> {
  try {
    await capture("git", ["merge-base", "--is-ancestor", older, newer]);
    return true;
  } catch (error) {
    if (error instanceof Error && "code" in error && error.code === 1) return false;
    throw error;
  }
}
