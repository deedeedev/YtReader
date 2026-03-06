package com.deedeedev.ytreader.data

const val DEFAULT_AI_MODEL = "gpt-4o-mini"

val DEFAULT_AI_CLEANING_PROMPT = """
Clean the subtitle text with minimal edits.

Goals:
- Join broken subtitle fragments into natural sentence flow.
- Add only missing punctuation.
- Fix only obvious transcription mistakes.

Constraints:
- Preserve original meaning, sentence order, and tone.
- Stay as close as possible to the original wording.
- Do not summarize, rewrite, translate, censor, or add new information.
- Return only the cleaned text.
""".trimIndent()
