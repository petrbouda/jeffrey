export type ReplayStartMode = 'beginning' | 'custom'
export type ReplayEndMode = 'latest' | 'custom'

export interface SelectedSession {
  id: string
  sessionInstance: string
}
