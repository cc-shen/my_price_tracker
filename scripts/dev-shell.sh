#!/usr/bin/env bash
set -euo pipefail

venv_dir=".venv"

if [ ! -d "${venv_dir}" ]; then
  make pre-commit-install
fi

source "${venv_dir}/bin/activate"
