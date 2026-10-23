#!/usr/bin/env bash
# PreToolUse hook (matcher: Bash). Before any `git push`:
#   1. rebases on origin/main, only if main received new commits;
#   2. compiles and runs the tests (./mvnw test);
# Exit code 2 blocks the push and feeds the message back to Claude.

input=$(cat)
echo "$input" | grep -Eq '"command"[[:space:]]*:[[:space:]]*"[^"]*git[[:space:]]+push' || exit 0

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0
git_dir=$(git rev-parse --git-dir)

block() { echo "PUSH CANCELLED: $1" >&2; exit 2; }

# A rebase left in progress by a previous attempt must be finished first
if [ -d "$git_dir/rebase-merge" ] || [ -d "$git_dir/rebase-apply" ]; then
  block "a rebase is still in progress. Resolve the conflicts, 'git add' the files, run 'git rebase --continue', then push again."
fi

git fetch origin main --quiet || block "unable to fetch origin/main."

# 1. Rebase only if main has new commits
behind=$(git rev-list --count HEAD..origin/main)
if [ "$behind" -gt 0 ]; then
  if ! out=$(git pull --rebase origin main 2>&1); then
    conflicts=$(git diff --name-only --diff-filter=U)
    block "merge conflicts while running 'git pull --rebase origin main'.
Conflicting files:
$conflicts

The rebase is left in progress. Do NOT resolve silently: show the user the conflicts and your proposed resolution for each file, wait for their explicit validation, then apply it, 'git add', 'git rebase --continue' and push again.
(To give up: 'git rebase --abort'.)
Git output:
$out"
  fi
fi

# 2. Compilation + tests
if ! out=$(./mvnw -B -q test 2>&1); then
  block "compilation or tests failed. Fix the errors before pushing.
$(echo "$out" | tail -n 60)"
fi

exit 0
