# AGENTS.md

## Default Execution Policy

- Continue tasks end-to-end without intermediate confirmation when the request is clear.
- Make reasonable assumptions and proceed; report assumptions in the final summary.
- Ask questions only when blocked by missing critical info or hard permission limits.
- Provide progress briefly during work and a single consolidated final report.

## Autonomous Continuation

- Do not stop at analysis or partial output; carry work through implementation and verification in the same turn whenever feasible.
- If a fix attempt fails, immediately try the next reasonable approach instead of waiting for user confirmation.
- Batch related actions (edit, run checks, apply follow-up fixes) and finish to a stable end state before reporting.
- Reserve interruption for true blockers only: missing secrets, external account access, or explicit user hold.

## Git Workflow

- Create small checkpoint commits during substantial progress, not only at the very end.
- Push checkpoints to the current remote branch during ongoing work when the branch is in a runnable state.
- Keep commit messages clear and scoped by feature or fix.

## Completion Criteria

- Implement requested changes directly in code/docs.
- Run relevant validation (build/test/lint) when feasible.
- Report: changed files, why changed, verification result, and any remaining issues.

## Engineering Standards

- Refactor with clean-code principles: small functions, clear names, single responsibility, and explicit boundaries.
- Implement with senior-level quality: predictable structure, defensive error handling, and testability-first design.
- Prefer MSA-oriented component boundaries: separate domain capabilities into loosely coupled modules/services with clear contracts.
- Avoid ad-hoc quick fixes that increase coupling or technical debt unless explicitly requested.

## Safety Boundaries

- Do not run destructive commands (`rm -rf`, `git reset --hard`, forced history rewrite) unless explicitly requested.
- Do not revert unrelated existing user changes.
- Follow sandbox and approval constraints; if escalation is required, request it with minimal scope.
