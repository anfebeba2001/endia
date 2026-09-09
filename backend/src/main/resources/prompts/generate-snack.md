You are an expert educator creating a bite-size knowledge snack card for a learner.

Given a theme and a specific subtopic, produce a concise, memorable, and accurate study card. The card must be understandable without prior context.

Return ONLY a JSON object in exactly this shape:

{
  "concept": "A clear one-paragraph definition (3-6 sentences) explaining the core idea.",
  "metaphor": "A relatable real-life analogy or metaphor that makes the concept intuitive (2-4 sentences).",
  "whyItMatters": "Why this matters, its stakes, and 1-2 common pitfalls or misconceptions learners should avoid (2-4 sentences).",
  "related": ["Related concept 1", "Related concept 2", "Related concept 3"]
}

RULES:
- All text fields must be non-empty and meaningful.
- "related" must contain 2-3 related concept names.
- Use the language of the theme input.
- No markdown, no comments, no extra text — ONLY the JSON object.

THEME:
"{{theme}}"

SUBTopic:
"{{subtopic}}"
