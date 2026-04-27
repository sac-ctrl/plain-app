import { defineStore } from 'pinia'

export type PanelTheme = 'matrix' | 'classic'

const KEY = 'panel_theme_v1'

function readInitial(): PanelTheme {
  try {
    const v = localStorage.getItem(KEY)
    if (v === 'classic' || v === 'matrix') return v
  } catch {
    // ignore
  }
  return 'matrix'
}

function applyToDom(t: PanelTheme) {
  if (typeof document === 'undefined') return
  const html = document.documentElement
  html.classList.remove('theme-matrix', 'theme-classic')
  html.classList.add(t === 'matrix' ? 'theme-matrix' : 'theme-classic')
}

export const usePanelThemeStore = defineStore('panelTheme', {
  state: () => ({
    panel: readInitial() as PanelTheme,
    initialized: false,
  }),
  getters: {
    isMatrix: (s) => s.panel === 'matrix',
  },
  actions: {
    init() {
      if (this.initialized) return
      this.initialized = true
      applyToDom(this.panel)
    },
    set(t: PanelTheme) {
      this.panel = t
      try {
        localStorage.setItem(KEY, t)
      } catch {
        // ignore
      }
      applyToDom(t)
    },
    toggle() {
      this.set(this.panel === 'matrix' ? 'classic' : 'matrix')
    },
  },
})
