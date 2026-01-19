# Steve AI

An AI chatbot that answers Minecraft questions in chat.

## Chat Usage

Mention `@Steve` anywhere in chat to ask a question:

```
Hey @Steve, how do I find diamonds?
```

Steve responds with:
- A concise answer (1-2 sentences)
- Clickable source links (if using Anthropic)
- Cost per query (if using Anthropic)

**Example response:**
```
Steve: Mine at Y level -59 for the highest diamond spawn rate. Branch mining at this level works best. ([1] | 0.1¢)
```

Hover over "Steve" to see model info and response time.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/steve` | `smp.steve.admin` | View current model status |
| `/steve model` | `smp.steve.admin` | List available providers |
| `/steve model <provider>` | `smp.steve.admin` | Switch provider |
| `/steve history [page]` | `smp.steve` | View your question history |

## Conversation Context

Steve remembers recent conversation for follow-up questions:

```
@Steve what's the best Y-level for diamonds?
Steve: Mine at Y -59 for best rates...

@Steve how long does it usually take?
Steve: Depends on your efficiency enchantment...
```

Context chains when messages are within 60 seconds of each other (max 5 turns).

## Cooldown

Default 10-minute cooldown between questions per player. Persists across server restarts.

## Model Providers

### Anthropic (Claude)

Cloud-based with web search integration.

**Features:**
- Web search (minecraft.wiki only)
- Source citations with clickable links
- Cost tracking (~0.1-3¢ per query)
- Prompt caching reduces costs by ~90%

**Configuration:**
```yaml
steve:
  model: anthropic
  anthropic:
    api-key: "sk-ant-..."
    max-searches: 3
```

### LM Studio

Run local LLMs for free, private queries.

**Features:**
- No cost (local model)
- No web search
- Privacy (all local)
- Quality depends on model

**Configuration:**
```yaml
steve:
  model: lmstudio
  lmstudio:
    endpoint: "http://localhost:1234"
    model: "your-model-name"
```

## Provider Comparison

| Feature | Anthropic | LM Studio |
|---------|-----------|-----------|
| Web Search | Yes | No |
| Citations | Yes | No |
| Cost | ~1-3¢/query | Free |
| Privacy | Cloud | Local |
| Quality | High | Varies |

## Configuration

```yaml
steve:
  enabled: true
  model: anthropic
  cooldown-seconds: 600

  anthropic:
    api-key: ""
    max-searches: 3

  lmstudio:
    endpoint: "http://localhost:1234"
    model: ""
```

| Option | Description |
|--------|-------------|
| `enabled` | Enable/disable Steve |
| `model` | Provider: `anthropic` or `lmstudio` |
| `cooldown-seconds` | Per-player cooldown |
| `anthropic.api-key` | Your Anthropic API key |
| `anthropic.max-searches` | Web searches per question |
| `lmstudio.endpoint` | LM Studio server URL |
| `lmstudio.model` | Model identifier |

## Permissions

| Permission | Description |
|------------|-------------|
| `smp.steve` | Ask Steve questions, view history |
| `smp.steve.admin` | View status, switch models |
