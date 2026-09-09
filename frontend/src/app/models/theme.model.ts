export interface Subtopic {
  id: string;
  name: string;
}

export interface ThemeSuggestResponse {
  themeId: string;
  subtopics: Subtopic[];
}
