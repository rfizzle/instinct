# `.ai/` — agent-shared assets

Single, vendor-neutral home for everything AI agents (Claude Code, Jules, future tools) read when working on this repo.

| Path | What lives there |
|---|---|
| `skills/` | Domain skills (`mc-*/SKILL.md`). Claude Code auto-loads via `.claude/skills` symlink; Jules reads them when `AGENTS.md` points to them. |

The CI prompts (`code-reviewer.md`, `spec-writer.md`) and the review criteria
(`review-criteria.yml`) are **not** kept here — the generic suite defaults in
[rfizzle/concord](https://github.com/rfizzle/concord) `.ai/` are used.
Domain-specific review emphasis lives in `AGENTS.md`, which the workflows
always inject into the prompt.

Resolution order in the reusable workflows: explicit `prompt-file` /
`criteria-file` workflow input → repo-local `.ai/` file (whole-file override)
→ concord default. To specialize a prompt or the scored categories for this
repo, copy the concord default into `.ai/` and edit it.

## Adding a new skill

1. Create `skills/<name>/SKILL.md` with the standard frontmatter (`name`, `description`).
2. Add a row to the "Working with domain skills" table in `AGENTS.md` so Jules knows when to read it.
