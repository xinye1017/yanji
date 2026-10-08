/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./App.{js,jsx,ts,tsx}", "./src/**/*.{js,jsx,ts,tsx}"],
  presets: [require("nativewind/preset")],
  theme: {
    extend: {
      colors: {
        // Light mode — soft blue-gray family (Compose: YanjiBackground/Surface family)
        yanji: {
          bg: "#F2F4F7",
          surface: "#FFFFFF",
          elevated: "#F1F5FB",
          floating: "#FFFFFF",
          "text-primary": "#172033",
          "text-secondary": "#5A6473",
          "text-tertiary": "#606A7C",
          "text-disabled": "#98A2B3",
          accent: "#356AE6",
          "accent-strong": "#2453BF",
          "accent-soft": "#EAF1FF",
          success: "#2F9E6D",
          warning: "#E67E22",
          danger: "#D64545",
          "field-border": "#7E8DA1",
          // Dark mode — Midnight Blue family (Compose: YanjiDark* tokens).
          // #000000 pure black is strictly forbidden (AGENTS.md §三.6).
          "dark-bg": "#0D111A",
          "dark-surface": "#151B28",
          "dark-elevated": "#1D2536",
          "dark-floating": "#222C40",
          "dark-text-primary": "#F0F4FC",
          "dark-text-secondary": "#94A3B8",
          "dark-text-tertiary": "#8797AC",
          "dark-text-disabled": "#475569",
          "dark-accent": "#4F7DF3",
          "dark-accent-strong": "#7197F7",
          "dark-accent-soft": "#294F7DF3",
          "dark-success": "#34D399",
          "dark-warning": "#FBBF24",
          "dark-danger": "#F87171",
          "dark-field-border": "#6B7A91",
        },
      },
      borderRadius: {
        // YanjiRadius semantic scale — raw dp literals forbidden at call sites.
        yanji: {
          xs: "4px",
          sm: "8px",
          md: "12px",
          lg: "16px",
          xl: "24px",
          xxl: "28px",
          full: "9999px",
        },
      },
      spacing: {
        yanji: {
          xs: "4px",
          sm: "8px",
          md: "12px",
          lg: "16px",
          xl: "24px",
          xxl: "32px",
        },
      },
    },
  },
  plugins: [],
};
