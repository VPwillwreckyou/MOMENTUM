import type { AIConfig, AIPlan, PlannerAnswers } from '../state/types';
import { toDateInput } from './dates';
import { parseAIJson, normalizeAIPlan } from './validation';
import { decryptSecret } from './vault';
import { ApiError, apiRequest } from './api';

interface ChatMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
}

interface ChatResponse {
  text: string;
  provider: string;
  model: string;
}

const PLAN_SYSTEM_PROMPT = `You are the goal architect inside MOMENTUM, a premium personal goal-management application.
Your job is to turn a person's ambition into a grounded, specific plan based on their real situation.

Rules:
- Be concrete and personal. Refer to the details the user gave you. Never produce generic advice.
- A milestone is an outcome or checkpoint, not a task. Use 3-4 milestones with 2-3 concrete tasks each.
- Tasks begin with an action verb and are completable in one sitting unless the user's available time says otherwise.
- Sequence work realistically: early tasks reduce uncertainty or build the foundation.
- Respect the user's stated time budget, constraints, target date and current situation.
- If the target date is unrealistic, create the best possible plan and make the scope honest rather than inventing a miracle.
- Use ISO calendar dates (YYYY-MM-DD) for dueDate. All dates must be today or later and should not pass the target date unless unavoidable.
- Suggested deadline should match the target date when sensible.
- Keep the plan compact and terse: no more than 12 tasks total, and 1-2 starting moves. Descriptions must be under 25 words.
- Return only valid JSON. Do not wrap it in markdown.

JSON shape:
{
  "title": "specific goal title",
  "description": "2-4 sentences explaining the approach and why it fits this person",
  "suggestedDeadline": "YYYY-MM-DD",
  "milestones": [
    {
      "title": "milestone outcome",
      "description": "what will be true when this is done",
      "dueDate": "YYYY-MM-DD",
      "tasks": [
        { "title": "verb-led action", "notes": "optional short context", "dueDate": "YYYY-MM-DD", "priority": "low|medium|high|critical" }
      ]
    }
  ],
  "firstTasks": [
    { "title": "quick first move", "notes": "optional", "dueDate": "YYYY-MM-DD", "priority": "medium" }
  ]
}`;

async function apiKeyFor(config: AIConfig): Promise<string | null> {
  if (!config.apiKeyCipher) return null;
  try {
    return await decryptSecret(config.apiKeyCipher);
  } catch {
    throw new Error('Your saved API key could not be unlocked on this device. Re-enter it in Settings.');
  }
}

async function chat(config: AIConfig, messages: ChatMessage[], json: boolean, signal?: AbortSignal): Promise<ChatResponse> {
  const apiKey = await apiKeyFor(config);
  return apiRequest<ChatResponse>('/api/ai/chat', {
    method: 'POST',
    body: {
      provider: config.provider,
      apiKey: apiKey ?? undefined,
      model: config.model,
      baseUrl: config.baseUrl,
      messages,
      json,
    },
    signal,
  });
}

export async function testAIConnection(config: AIConfig): Promise<string> {
  const response = await chat(
    config,
    [
      { role: 'system', content: 'You are a connection test. Reply with exactly one short sentence confirming you are ready.' },
      { role: 'user', content: 'Reply READY and name your model.' },
    ],
    false,
  );
  return response.text.trim().slice(0, 300);
}

export async function generateAIPlan(
  config: AIConfig,
  answers: PlannerAnswers,
  signal?: AbortSignal,
): Promise<AIPlan> {
  const userPrompt = `Interview answers:
1. Goal: ${answers.goal}
2. Current situation: ${answers.situation || 'Not provided'}
3. Definition of success: ${answers.success || 'Not provided'}
4. Available time: ${answers.timeCommitment || 'Not provided'}
5. Target date: ${answers.targetDate || 'No fixed date'}
6. Constraints or obstacles: ${answers.constraints || 'None stated'}

Today is ${toDateInput()}. Build the plan now.`;
  const response = await chat(
    config,
    [
      { role: 'system', content: PLAN_SYSTEM_PROMPT },
      { role: 'user', content: userPrompt },
    ],
    true,
    signal,
  );
  let parsed: unknown;
  try {
    parsed = parseAIJson(response.text);
  } catch (error) {
    throw new ApiError(error instanceof Error ? error.message : 'The AI returned an unreadable plan.', 502);
  }
  return normalizeAIPlan(parsed);
}

export function aiConfigurationIssue(config: AIConfig): string | null {
  if (!config.model.trim()) return 'Choose a model in AI settings.';
  if (config.provider !== 'ollama' && !config.baseUrl.trim()) return 'Add the provider base URL in AI settings.';
  return null;
}

export { chat as rawAIChat };
