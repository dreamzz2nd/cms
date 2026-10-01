# Mandatory Design Standard: Anti-Slop Design

Before designing, scaffolding, modifying, or creating any UI, frontend code, templates, CSS, Tailwind classes, or layout components in this repository, you MUST always read and strictly adhere to the `anti-slop-design` skill located at:
- `file:///c:/Users/user/nyamimo/.agents/skills/anti-slop-design/SKILL.md`
- `file:///C:/Users/user/.gemini/config/skills/anti-slop-design/SKILL.md`

## Key Mandates:
1. **Zero AI Slop**: No generic indigo/purple radial gradient background blobs, no raw emojis as functional icons (use single-weight vector SVG like Lucide), no cookie-cutter 3-card grids, no fake stats.
2. **1-to-3 Focus Rule**: 1 primary action per screen section, max 3 secondary actions.
3. **8-State Interactive Feedback**: Default, Hover, Focus-Visible, Active/Press (tactile scale 0.97), Disabled, Loading/Spinner, Error, Success.
4. **Spacing & Geometry**: Strict 4px/8pt grid math, exact nested corner radii formula ($R_{inner} = \max(0, R_{outer} - P)$).
5. **7-Axis Quality Gate**: Every UI implementation must pass the 7-axis pre-emit quality checklist before shipping.
