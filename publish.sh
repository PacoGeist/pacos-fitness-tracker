#!/usr/bin/env bash
# Publish a new version of Paco's Fitness Tracker to GitHub.
#
# Run it from Git Bash (./publish.sh), or double-click it in File Explorer.
# It commits all changes, pushes them to GitHub, and tags the version (for example v1.6).
# GitHub then builds the signed app and creates the release that phones get from
# Settings > App updates > Check for updates.

cd "$(dirname "$0")" || exit 1
REPO_URL="https://github.com/PacoGeist/pacos-fitness-tracker"

pause() { echo; read -r -p "Press Enter to close..." _; }
fail() {
  echo
  echo "Problem: $*"
  echo "Nothing more was done. Fix the problem above and run this again."
  pause
  exit 1
}

command -v git >/dev/null 2>&1 || fail "Git is not installed. Get it from https://git-scm.com/download/win"
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || fail "This folder is not a Git clone of the repository."
[ -n "$(git config user.name)" ] && [ -n "$(git config user.email)" ] ||
  fail "Git doesn't know who you are yet. Run:  git config --global user.name \"PacoGeist\"  and  git config --global user.email \"you@example.com\""

VER=$(grep '^APP_VERSION_NAME=' gradle.properties | cut -d= -f2 | tr -d '[:space:]')
[ -n "$VER" ] || fail "Could not read APP_VERSION_NAME from gradle.properties."
grep -q "const APP_VERSION = '$VER'" app/src/main/assets/index.html ||
  fail "The version in app/src/main/assets/index.html does not match $VER in gradle.properties. Make them the same."

echo "Checking GitHub for versions that are already published..."
git fetch --tags --quiet origin 2>/dev/null || true
if git rev-parse -q --verify "refs/tags/v$VER" >/dev/null; then
  fail "Version $VER has already been published. Raise APP_VERSION_NAME and APP_VERSION_CODE in gradle.properties and APP_VERSION in app/src/main/assets/index.html."
fi

echo
git status --short
echo
read -r -p "Publish version $VER to GitHub? [y/N] " answer
case "$answer" in
  y|Y|yes|Yes|YES) ;;
  *) echo "Cancelled. Nothing was published."; pause; exit 0 ;;
esac

git add -A || fail "Could not prepare the changed files."
if ! git diff --cached --quiet; then
  git commit -q -m "Version $VER" || fail "Could not save (commit) the changes."
fi
git branch -M main || fail "Could not name the branch 'main'."
git push -u origin main || fail "Could not send the changes to GitHub. Check your internet connection and GitHub sign-in."
git tag "v$VER" || fail "Could not create the version tag v$VER."
git push origin "v$VER" || fail "Could not send the version tag to GitHub."

echo
echo "Done! GitHub is now building version $VER (about 5 minutes)."
echo "Progress: $REPO_URL/actions"
echo "Release:  $REPO_URL/releases/latest"
if command -v start >/dev/null 2>&1; then start "" "$REPO_URL/actions"; fi
pause
