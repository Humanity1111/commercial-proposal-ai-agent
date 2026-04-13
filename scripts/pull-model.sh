#!/bin/bash
# Доступные модели gemma4:
#   gemma4:e2b  — Effective 2B, ~7.2 GB  (лёгкая, для edge/dev)
#   gemma4:e4b  — Effective 4B, ~9.6 GB  (баланс качество/размер)
#   gemma4:26b  — MoE 26B,     ~18 GB   (frontier, нужно ≥24 GB VRAM)
#   gemma4:31b  — Dense 31B,   ~20 GB   (максимум, нужно ≥24 GB VRAM)
MODEL=${1:-gemma4:e2b}
echo "Pulling $MODEL model into Ollama container..."
docker compose exec ollama ollama pull "$MODEL"
echo "Done! Model $MODEL ready."
