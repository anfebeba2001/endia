<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan
<!-- SPECKIT END -->

## Active Technologies
- Java 21 (backend), TypeScript 5.x (frontend), Angular 21 (001-batch-mvp-enhancements)
- MySQL 8.x. New entities: `StudySession`, `StudySessionItem`. Extended entities: `Subtopic` (+`source`), `QuizQuestion` (+`type`,`variant`,`drag_drop_json`), `AnswerAttempt` (+`arrangement` JSON, `chosen_index` now nullable). New converter: `JpaJsonObjectConverter` for structured drag-drop JSON. (001-batch-mvp-enhancements)

## Recent Changes
- 001-batch-mvp-enhancements: Added Java 21 (backend), TypeScript 5.x (frontend), Angular 21
