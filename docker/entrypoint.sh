#!/usr/bin/env sh
set -eu

CONFIG_DIR="${HOME}/.cognis"
CONFIG_PATH="${CONFIG_DIR}/config.json"
WORKSPACE_PATH="${COGNIS_WORKSPACE:-${CONFIG_DIR}/workspace}"

mkdir -p "${CONFIG_DIR}" "${WORKSPACE_PATH}"

if [ "${COGNIS_WRITE_CONFIG:-true}" = "true" ] || [ ! -f "${CONFIG_PATH}" ]; then
  cat > "${CONFIG_PATH}" <<JSON
{
  "agents": {
    "defaults": {
      "workspace": "${WORKSPACE_PATH}",
      "provider": "${COGNIS_PROVIDER:-openrouter}",
      "model": "${COGNIS_MODEL:-anthropic/claude-opus-4-5}",
      "maxToolIterations": 20
    }
  },
  "providers": {
    "openrouter": {
      "apiKey": "${OPENROUTER_API_KEY:-}",
      "apiBase": "${OPENROUTER_API_BASE:-https://openrouter.ai/api/v1}"
    },
    "openai": {
      "apiKey": "${OPENAI_API_KEY:-}",
      "apiBase": "${OPENAI_API_BASE:-https://api.openai.com/v1}"
    },
    "anthropic": {
      "apiKey": "${ANTHROPIC_API_KEY:-}",
      "apiBase": "${ANTHROPIC_API_BASE:-https://api.anthropic.com/v1}"
    }
  },
  "tools": {
    "web": {
      "search": {
        "apiKey": "${WEB_SEARCH_API_KEY:-}",
        "maxResults": 5
      }
    }
  }
}
JSON
fi

if [ "$#" -eq 0 ]; then
  set -- gateway --port "${COGNIS_GATEWAY_PORT:-8787}"
fi

exec java -cp "/app/cognis-app.jar:/app/lib/*" io.cognis.app.CognisApplication "$@"
