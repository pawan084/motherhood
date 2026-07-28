#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ "${SITES_ENV_READY:-}" != "1" ]]; then
  exec "${script_dir}/sites-env.sh" -- "$0" "$@"
fi

command -v timeout >/dev/null || {
  echo "build-verified.sh requires GNU timeout." >&2
  exit 69
}

vinext="${SITES_PROJECT_ROOT}/node_modules/.bin/vinext"
if [[ ! -x "${vinext}" ]]; then
  echo "vinext is unavailable at ${vinext}." >&2
  # The usual cause is not a missing install but a HOISTED one: if some parent
  # package.json declares this app as an npm workspace, npm installs everything
  # into the repo-root node_modules and nothing lands here. This toolchain is
  # deliberately self-contained (own lockfile, own .sites-runtime npm cache), so
  # say that plainly instead of sending people round the install loop again.
  hoisted=""
  probe="$(cd "${SITES_PROJECT_ROOT}/.." && pwd)"
  while [[ "${probe}" != "/" && -n "${probe}" ]]; do
    if [[ -x "${probe}/node_modules/.bin/vinext" ]]; then
      hoisted="${probe}"
      break
    fi
    next="$(cd "${probe}/.." && pwd)"
    [[ "${next}" == "${probe}" ]] && break
    probe="${next}"
  done
  if [[ -n "${hoisted}" ]]; then
    echo "" >&2
    echo "Found it hoisted at ${hoisted}/node_modules/.bin/vinext." >&2
    echo "Something above this app is treating it as an npm workspace. This app" >&2
    echo "must install into its OWN node_modules: remove the \"workspaces\" entry" >&2
    echo "from ${hoisted}/package.json, then reinstall here." >&2
  else
    echo "Run 'npm install' (or 'npm run install:ci') in ${SITES_PROJECT_ROOT} first." >&2
  fi
  exit 69
fi

echo "Running bounded vinext build..."
timeout \
  --signal=TERM \
  --kill-after="${SITES_BUILD_KILL_AFTER:-10s}" \
  "${SITES_BUILD_TIMEOUT:-3m}" \
  "${vinext}" build

"${script_dir}/validate-artifact.sh"
