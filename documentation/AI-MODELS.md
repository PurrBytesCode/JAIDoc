# AI Models

JAIDoc is designed to work entirely with **local AI models** — no cloud APIs required. The project is developed and
tested against the Llama.cpp Server in routing mode, which dynamically selects the best model for each query based on
complexity.

## Hardware

| Component     | Specification                          |
|---------------|----------------------------------------|
| CPU           | Intel Core Ultra 9 275HX               |
| RAM           | 32 GB DDR5-6400                        |
| GPU 1 (local) | NVIDIA RTX 5070 Ti 12GB Mobile         |
| GPU 2 (eGPU)  | NVIDIA RTX 3090 24GB via Thunderbolt 4 |

## AI Server

JAIDoc uses **llama.cpp Server** in routing mode to dynamically select the best model for each query based on
complexity.

| Component | Technology       | Version | Date       |
|-----------|------------------|---------|------------|
| Server    | llama.cpp Server | b9704   | 2026-06-18 |

## Models

### Model Info

| Model                                         | Quantization | Context Size | URL                                                                               |
|-----------------------------------------------|:------------:|:------------:|-----------------------------------------------------------------------------------|
| LFM2.5-8B-A1B                                 |  UD-IQ4_NL   |  256K (MAX)  | https://huggingface.co/unsloth/LFM2.5-8B-A1B-GGUF                                 |
| Mellum2-12B-A2.5B                             |    Q4_K_M    |  128K (MAX)  | https://huggingface.co/JetBrains/Mellum2-12B-A2.5B-Thinking-GGUF-Q4_K_M           |
| Nex-N2-mini                                   |    IQ4_NL    |  256K (MAX)  | https://huggingface.co/bartowski/nex-agi_Nex-N2-mini-GGUFF                        |
| NVIDIA-Nemotron-3-Nano-Omni-30B-A3B-Reasoning |  IQ4_NL_XL   |  256K (MAX)  | https://huggingface.co/unsloth/NVIDIA-Nemotron-3-Nano-Omni-30B-A3B-Reasoning-GGUF |
| NVIDIA-Nemotron-Cascade-2-30B-A3B             |    IQ4_NL    |   1M (MAX)   | https://huggingface.co/bartowski/nvidia_Nemotron-Cascade-2-30B-A3B-GGUF           |
| Qwen3.6-27B                                   |    IQ4_NL    |  256K (MAX)  | https://huggingface.co/unsloth/Qwen3.6-27B-GGUF                                   |
| Qwen3.6-27B-MTP                               |    IQ4_NL    |  256K (MAX)  | https://huggingface.co/unsloth/Qwen3.6-27B-MTP-GGUF                               |
| Qwen3.6-35B-A3B                               | UD-IQ4_NL_XL |  256K (MAX)  | https://huggingface.co/unsloth/Qwen3.6-35B-A3B-GGUF                               |
| Qwen3.6-35B-A3B-MTP                           |    IQ4_NL    |  256K (MAX)  | https://huggingface.co/unsloth/Qwen3.6-35B-A3B-MTP-GGUF                           |
| Qwopus3.5-9B                                  |    Q4_K_M    |  256K (MAX)  | https://huggingface.co/Jackrong/Qwopus3.5-9B-v3-GGUF                              |
| Qwopus3.5-9B-Coder                            |    IQ4_XS    |  256K (MAX)  | https://huggingface.co/Jackrong/Qwopus3.5-9B-Coder-GGUF                           |
| Qwopus3.6-27B-Coder                           |    IQ4_XS    |  256K (MAX)  | https://huggingface.co/Jackrong/Qwopus3.6-27B-Coder-GGUF                          |
| Qwopus3.6-27B-Coder-MTP                       |    IQ4_XS    |  256K (MAX)  | https://huggingface.co/Jackrong/Qwopus3.6-27B-Coder-MTP-GGUF                      |
| Qwopus3.6-27B-v2                              |    IQ4_XS    |  256K (MAX)  | https://huggingface.co/Jackrong/Qwopus3.6-27B-v2-GGUF                             |
| Qwopus3.6-27B-v2-MTP                          |    IQ4_XS    |  256K (MAX)  | https://huggingface.co/Jackrong/Qwopus3.6-27B-v2-MTP-GGUF                         |
| Qwopus3.6-35B-A3B-v1                          |    IQ4_XS    |  256K (MAX)  | https://huggingface.co/Jackrong/Qwopus3.6-35B-A3B-v1-GGUF                         |
| Qwopus3.6-35B-A3B-v1-agents                   |    IQ4_XS    |  256K (MAX)  | https://huggingface.co/Jackrong/Qwopus3.6-35B-A3B-v1-GGUF                         |
| gemma-4-12B-it                                |    IQ4_NL    |  128K (MAX)  | https://huggingface.co/unsloth/gemma-4-12b-it-GGUF                                |
| gemma-4-26B-A4B-it                            |  UD-IQ4_NL   |  256K (MAX)  | https://huggingface.co/unsloth/gemma-4-26B-A4B-it-GGUF                            |
| gemma-4-31B-it                                |    IQ4_NL    |     128K     | https://huggingface.co/unsloth/gemma-4-31B-it-GGUF                                |

### Model Performance

| Model                                         | Parallel-Slots | Preferred agent    | Max (T/S) | Task             |
|-----------------------------------------------|:--------------:|:-------------------|----------:|:-----------------|
| LFM2.5-8B-A1B                                 |       1        | Junie              |           | Simple Code      |
| Mellum2-12B-A2.5B                             |       1        | AI Assistant       |           | Single task code |
| Nex-N2-mini                                   |       1        | Junie, Claude Code |       105 | Very Hard Code   |
| NVIDIA-Nemotron-3-Nano-Omni-30B-A3B-Reasoning |       1        | Junie              |       130 | General          |
| NVIDIA-Nemotron-Cascade-2-30B-A3B             |       1        | Junie              |       160 | Single task code |
| Qwen3.6-27B                                   |       1        | Junie              |        50 | Very Hard Code   |
| Qwen3.6-27B-MTP                               |       1        | Junie              |        50 | Very Hard Code   |
| Qwen3.6-35B-A3B                               |       1        | Junie              |       100 | Hard Code        |
| Qwopus3.5-9B                                  |       1        | Claude Code        |           | Code             |
| Qwopus3.5-9B-Coder                            |       1        | Claude Code        |           | Hard Code        |
| Qwopus3.6-27B-Coder                           |       1        | Claude Code        |        40 | Very Hard Code   |
| Qwopus3.6-27B-Coder-MTP                       |       1        | Claude Code        |        40 | Very Hard Code   |
| Qwopus3.6-27B-v2                              |       1        | Claude Code        |        50 | Very Hard Code   |
| Qwopus3.6-27B-v2-MTP                          |       1        | Claude Code        |        50 | Very Hard Code   |
| Qwopus3.6-35B-A3B-v1                          |       1        | Claude Code        |       117 | Hard Code        |
| Qwopus3.6-35B-A3B-v1-agents                   |       2        | Claude Code        |        60 | Hard Code        |
| gemma-4-12B-it                                |       1        | Junie,Claude Code  |           | Code             |
| gemma-4-26B-A4B-it                            |       1        | Junie,Claude Code  |           | Hard Code        |
| gemma-4-31B-it                                |       1        | Junie,Claude Code  |           | Very Hard Code   |

## AI Agents

The project is developed using multiple AI coding agents, each with different strengths:

| Agent                 | IDE / Platform | Purpose                                                                       |
|-----------------------|----------------|-------------------------------------------------------------------------------|
| Claude Code           | Terminal       | Primary agent — deep research, complex refactors, and architectural decisions |
| IntelliJ AI Assistant | IntelliJ IDEA  | Inline code completion, quick suggestions, and minor fixes within the editor  |
| Junie                 | Terminal       | Alternative agent for comparison — experimental use and secondary opinions    |
