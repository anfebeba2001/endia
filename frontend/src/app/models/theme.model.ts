export type SubtopicSource = 'AI_SUGGESTED' | 'USER_PROPOSED';

export interface Subtopic {
  id: string;
  name: string;
  source?: SubtopicSource;
}

export interface ThemeSuggestResponse {
  themeId: string;
  subtopics: Subtopic[];
}

export interface AddedSubtopic {
  id: string;
  name: string;
  source: SubtopicSource;
}

export interface SubtopicAddResponse {
  themeId: string;
  added: AddedSubtopic[];
  duplicates: string[];
}
