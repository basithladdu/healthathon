# Health-a-thon — Design Blueprint (Green Edition) and Reusable Build Prompt

## Green edition notes

This is the green-coloured edition of the Health-a-thon design blueprint. Structure, type scale, layout, spacing, radii, motion, accessibility rules and page recipe are unchanged from the original study. What changed:

- **Structural colours** move from deep navy and hospital blue to forest ink and clinical green.
- **Surfaces** move from pale blue-gray to pale green-gray (mist) and pale mint (ice).
- **Borders and shadows** keep their opacities but are tinted with forest ink instead of navy.
- **The identity gradient** runs green → emerald → teal instead of green → teal → indigo.
- **Kept on purpose:** the three domain accents (cancer, diabetes, maternal and child health) and the prize orange. They are categorical and reward signals, not brand colour; turning them green would make the categories indistinguishable.

All green values below pass WCAG AA against white at the sizes they are specified for (primary ≈ 8:1; muted ≈ 6.4:1; the lightest gradient stop is used only for 46px/800 display text, where 3:1 applies).

## Core public page gallery

The `pages/` folder preserves the stable public experience: `home.png`, `guide.png`, and `register.png`. These cover the landing page, FAQ/guide system, and registration entry without archiving changing participant or submission data.

> Use this document as the visual authority for future healthcare, research, social-impact, or institutional innovation websites inspired by the Health-a-thon design language. Preserve its hierarchy, color roles, spacing, card geometry, image treatment, and responsive behavior while replacing the source branding, copy, and protected assets with original project material.

## 1. Reference and audit scope

- Source audited: <https://healthathon.reskilll.com/>
- Live title: `Health-a-thon 2026 · India's First Doctor × Technology Health Hackathon`
- Audit date: 2 September 2026
- Desktop measurement viewport: `1280 × 720 CSS px`, DPR `2`
- Desktop document: approximately `1265 × 3945 CSS px`
- Mobile measurement viewport: `390 × 844 CSS px`
- Mobile document: approximately `375 × 7411 CSS px`, excluding the scrollbar
- Visual reference: [`healtha-ton.png`](healtha-ton.png)

This is a design-system study, not permission to copy partner logos, photography, trademarks, event copy, or medical claims. Use original or licensed assets in new projects.

## 2. Design concept

Create an institutional healthcare-innovation landing page that feels credible enough for doctors and research organizations, approachable enough for students and builders, and modern enough for an AI hackathon.

The visual formula is:

```text
clinical clarity + institutional trust + technology optimism
```

The page avoids stereotypical neon "AI" styling. It uses:

- White and pale green-gray backgrounds.
- Deep forest-green typography and controls.
- A restrained green-to-teal title gradient.
- Real clinical collaboration photography.
- Strong partner-logo visibility.
- Rounded white information cards with quiet shadows.
- Compact, evidence-oriented copy rather than oversized decorative sections.

## 3. Design personality

The interface should feel:

- Trustworthy and calm.
- Modern but not experimental.
- Inclusive across clinical and technical audiences.
- Information-dense without appearing crowded.
- Institutional, organized, and measurable.
- Human-centered rather than machine-centered.

Avoid:

- Dark cyberpunk backgrounds.
- Fluorescent gradients.
- Glassmorphism.
- Cartoon medical imagery.
- Red as a general brand color.
- Neon or lime greens.
- Heavy drop shadows.
- Excessive animation.
- Generic stock-photo grids.

## 4. Color system

### 4.1 Core tokens

```css
:root {
  /* Structural */
  --health-ink: #0a2118;
  --health-primary: #0c5c3f;
  --health-muted: #4d6259;
  --health-secondary-text: #2a3d35;

  /* Surfaces */
  --health-white: #ffffff;
  --health-mist: #f3f7f5;
  --health-ice: #e6f2ec;
  --health-border: rgb(10 33 24 / 0.10);
  --health-border-strong: rgb(10 33 24 / 0.22);
  --health-pale-green: #a3d2ba;

  /* Brand/title gradient */
  --health-green: #0b7a3e;
  --health-emerald: #12925a;
  --health-teal: #0f6f6a;
  --health-year-green: #0a4f33;

  /* Domain accents (intentionally not green) */
  --health-cancer: #1a5f9e;
  --health-diabetes: #a15c00;
  --health-maternal: #b83280;

  /* Rewards */
  --health-prize: #f5a623;
}
```

### 4.2 Color hierarchy

| Role | Color | Purpose |
|---|---:|---|
| Primary structural | `#0A2118` | Main headings, footer, dark feature panels, critical copy |
| Primary action | `#0C5C3F` | Filled CTAs, major display title, icons, links |
| Secondary copy | `#4D6259` | Paragraphs, partner descriptions, supporting labels |
| Alternating surface | `#F3F7F5` | Partner, eligibility, and prize sections |
| Pale utility surface | `#E6F2EC` | Chips, inset controls, technology partner strip |
| Border | `rgba(10,33,24,.10)` | Card edges and section rules |
| Prize accent | `#F5A623` | Prize amount, trophy details, reward emphasis |

### 4.3 Title gradient

Use the green gradient only for the event/product name or one comparable identity mark.

```css
.brand-gradient-text {
  background: linear-gradient(
    92deg,
    #0b7a3e 0%,
    #0b7a3e 38%,
    #12925a 52%,
    #0f6f6a 66%,
    #0f6f6a 100%
  );
  background-clip: text;
  color: transparent;
}
```

Do not use this gradient on buttons, large surfaces, every heading, or decorative backgrounds.

### 4.4 Domain accents

- Cancer: blue `#1A5F9E`.
- Diabetes: warm brown-orange `#A15C00`.
- Maternal and child health: magenta `#B83280`.
- Use the accents only on domain names, tiny icons, or category chips.
- Always include text labels; color is not the only identifier.
- These stay non-green in the green edition so the three domains remain distinguishable from the brand and from each other.

### 4.5 Surface rhythm

Alternate white and mist surfaces to separate dense informational modules without large empty gaps:

```text
white hero → mist partners → white use cases → pale design-scope panel
→ mist eligibility → white team structure → mist rewards → dark forest footer
```

## 5. Typography

### 5.1 Font stacks

```css
--font-display: "Sora", "Inter", system-ui, -apple-system, sans-serif;
--font-sans: "Inter", system-ui, -apple-system, sans-serif;
--font-mono: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
```

- `Sora` creates the rounded, modern institutional heading voice.
- `Inter` carries navigation, body copy, CTAs, labels, and metadata.
- Use monospace only for technical data if needed; it is not a major visual element in the audited page.

### 5.2 Weight system

- `800`: hero display, section H2s, card H3s, major numerical value.
- `700`: subtitle, CTA labels, compact overlines, important inline phrases.
- `600`: navigation, category names, emphasized body phrases.
- `400`: body text and descriptions.

### 5.3 Desktop type scale

| Element | Size | Line height | Weight | Tracking |
|---|---:|---:|---:|---:|
| Gradient event name | `46.4px` | `45.472px` | `800` | `-1.624px` |
| Year | `29.6px` | `29.6px` | `800` | `-0.74px` |
| Hero audience title | `58.4px` | `63.072px` | `800` | `-0.48px` |
| Hero action line | `32px` | `34.56px` | `800` | `-0.48px` |
| Hero subtitle | `19.2px` | `20.736px` | `700` | `-0.48px` |
| Centered section H2 | `37.6px` | `43.616px` | `800` | `-1.128px` |
| Compact section H2 | `29.6px` | `40.7px` | `800` | `-0.888px` |
| Eligibility H2 | `25.6px` | `26.88px` | `800` | `-0.768px` |
| Use-case H3 | `20px` | `28px` | `800` | `-0.6px` |
| Partner H3 | `15px` | `20.625px` | `800` | `-0.45px` |
| Body | `16px` | `24px` | `400` | normal |
| Small body | `13–14px` | `19.5–21px` | `400` | normal |
| Overline | `11px` | `11.55–16.5px` | `700` | `1.1px` |
| Nav/footer label | `11–13px` | `16.5–19.5px` | `600–700` | normal |

### 5.4 Mobile type behavior

- Gradient event name: `31.2px / 30.576px` at `390px` viewport.
- Hero audience title should scale to roughly `34–38px` with a compact two-line wrap.
- Section headings scale to approximately `28–32px`.
- Body remains `15–16px`; do not shrink the information to fit.
- Preserve bold hierarchy and negative tracking, but reduce line lengths before reducing font size.

### 5.5 Editorial rules

- Use title case or uppercase selectively; not every heading is uppercase.
- Major centered section headings may use sentence/title case.
- Functional sections such as "WHO CAN APPLY" and "TEAM STRUCTURE" use uppercase.
- Keep paragraphs calm and neutral; emphasis is achieved through bold inline phrases and color-coded domain names.
- Center only key section titles. Long descriptions and card copy remain left-aligned.

## 6. Layout system

### 6.1 Container

- Main maximum container: `1220px`.
- At the `1265px` desktop document width, content begins around `51px` from the left.
- Major two-column content ends around `1215px`.
- Mobile content width: approximately `335px` inside `20px` left/right margins at a `375px` document width.
- Use `max-width: 680px` for centered heading blocks.
- Long descriptive content can use `512–680px` measures depending on context.

### 6.2 Grid

- Desktop partner grid: five equal cards.
- Desktop use-case grid: two equal cards, approximately `572px` each with a `20px` gap.
- Desktop eligibility grid: six cards.
- Desktop rewards grid: asymmetric, approximately `0.95fr / 1.05fr`.
- Mobile: all of these become a single column.
- At `sm` widths, selected grids become two columns.
- At `lg` widths, restore five-, six-, or asymmetric desktop layouts.

### 6.3 Breakpoints

Use a Tailwind-like system:

```text
base: < 640px
sm:   ≥ 640px
lg:   ≥ 1024px
```

### 6.4 Spacing scale

The design uses a compact `4px` base scale:

```text
4, 6, 8, 10, 12, 14, 16, 20, 24, 28, 32, 36, 40, 48px
```

Common card gaps: `12, 16, 20px`.

This page does not use huge `112px` section padding. Its rhythm is tighter because the content is institutional and information-rich.

Observed desktop section padding:

- Partners: `36px 0 28px` after the hero.
- Use cases: approximately `24px 0 28px`.
- Design principles: approximately `20px 0 36px`.
- Eligibility: `40px 0`.
- Team: `24px 0`.
- Rewards: `48px 0`.

Observed mobile section padding:

- Partners: `24px 0 20px`.
- Use cases: `20px 0 24px`.
- Design principles: `16px 0 32px`.
- Eligibility: `32px 0`.
- Team: `24px 0`.
- Rewards: `40px 0`.

## 7. Shape system

### 7.1 Radii

```css
--radius-small: 10px;
--radius-icon: 11px;
--radius-medium: 14px;
--radius-card: 16px;
--radius-large-card: 20px;
--radius-feature: 22px;
--radius-hero-card: 24px;
--radius-pill: 9999px;
```

- White partner and eligibility cards: `14–16px`.
- Use-case cards: `24px`.
- Design-scope and team panels: `20–22px`.
- CTA buttons: pill-shaped.
- Prize panel: `20–24px`.
- Icon backgrounds: circular or softly rounded `10–12px` tiles.

### 7.2 Borders

- Default border: `1px solid rgb(10 33 24 / .05–.10)`.
- Outline CTA: `1.5px solid rgb(10 33 24 / .22)`.
- Inset pale-green controls: border near `#D3E6DC`.
- Section boundaries use a single low-opacity forest-ink rule.
- Do not use pure black borders.

### 7.3 Shadows

Default elevated card:

```css
box-shadow:
  0 1px 2px rgb(10 33 24 / .06),
  0 16px 40px -20px rgb(10 33 24 / .24);
```

Primary CTA:

```css
box-shadow: 0 8px 22px rgb(12 92 63 / .28);
```

Large feature panel may use:

```css
box-shadow:
  0 2px 4px rgb(10 33 24 / .07),
  0 32px 64px -24px rgb(10 33 24 / .30);
```

Keep shadows green-tinted, low-opacity, and close to the surface.

## 8. Header

### Desktop

- White fixed/sticky-looking navigation bar, approximately `80px` high.
- Left cluster presents multiple partner logos as one visual identity row.
- Middle/right area uses short text links.
- Primary register CTA is pinned at the far right.
- Use subtle bottom border, not a heavy shadow.
- Partner logos use `object-fit: contain` and consistent visual height, even when source aspect ratios differ.

### Mobile

- Header height around `66px`.
- Retain the five-logo lockup in a compressed row.
- Hide desktop text navigation and register CTA.
- Show a circular hamburger control at the right.
- At `390px`, content uses approximately `20px` side margins.

## 9. Hero composition

### 9.1 Desktop

- Hero follows the header with a white background.
- Split composition: copy on the left, clinical collaboration image on the right.
- Image occupies roughly the right `60%`; audited render approximately `759 × 600px`.
- Hero image source is a wide `960 × 641` photograph.
- Use `object-fit: cover` with `object-position: 62% 40%`.
- Apply white edge gradients/fades so the image blends into the background and the split does not feel like a hard rectangle.
- Left text column begins around `51px` and is approximately `544px` wide.
- Title sequence:
  1. Gradient event name with deep-green year.
  2. Smaller trust-setting subtitle.
  3. Large forest-ink audience pairing over two lines.
  4. Dark action/purpose statement.
  5. Domain line with three colored terms.
  6. Dual audience CTAs and supporting notes.
  7. Underlined role links.

### 9.2 Mobile

- Reorder the hero: image first, text second.
- The image becomes a landscape card inside the `20px` page margins.
- Remove the desktop-only full-bleed image layer rather than squeezing it.
- Event name begins below the image; measured at `31.2px`.
- Stack primary and secondary CTAs full-width.
- Supporting eligibility notes sit immediately below their respective CTAs.
- Role links remain compact and horizontally arranged where space allows.

### 9.3 Photography direction

- Prefer real doctors, researchers, patients, and technologists collaborating around a device or workflow.
- Use bright clinical interiors and natural neutral light.
- Avoid dramatic surgery imagery, isolated devices, diagnostic claims, or distress-focused patient photography.
- The subject should reinforce cooperation between clinical and technical expertise.

## 10. Components

### 10.1 Primary CTA

```css
.button-primary {
  min-height: 43px;
  padding: 10px 18px;
  border: 1.5px solid transparent;
  border-radius: 9999px;
  background: #0c5c3f;
  color: #fff;
  font: 700 13px/1.5 Inter, sans-serif;
  box-shadow: 0 8px 22px rgb(12 92 63 / .28);
}
```

Hero role CTA variant:

- Width approximately `264px` on desktop.
- Minimum height `51px`; doctor CTA may become `71px` because of its longer label.
- Padding `14px 24px`.
- Label `14.4px`, weight `700`.
- Mobile: full-width within the content column.

### 10.2 Secondary CTA

- Transparent/white background.
- Forest-green text.
- `1.5px` forest-ink border at `22%` opacity.
- No shadow by default.
- Same pill geometry as the primary CTA.

### 10.3 Partner card

- White card on mist background.
- `14–16px` radius.
- Compact top-aligned partner logo.
- Organization name in `15px` Sora ExtraBold.
- Description in `11–13px` Inter with comfortable line-height.
- Small color-coded text link at the bottom.
- Keep link baseline aligned across the row when possible.
- Desktop: five cards. Mobile: single stacked column.

### 10.4 Technology partner strip

- Centered below the institutional partner grid.
- Pale green rounded container, about `20px` radius.
- Logo left, short description right, small link below/inline.
- Overline above the strip: uppercase, `11px`, `1.1px` tracking.
- "Powered by" lockup sits below with generous separation.

### 10.5 Scope split panel

- Two side-by-side cards on desktop.
- Left: white, prominent non-clinical scope statement.
- Right: deep forest green, three short reassurance/checklist rows.
- Equal visual height and aligned rounded corners.
- Stack on mobile.
- Use outline/check icons in subdued green circles.

### 10.6 Use-case card

- White button-card, about `572 × 250px` on desktop.
- `24px` radius, `28px` padding.
- Thin low-opacity forest-ink border.
- Green-tinted shadow with long soft falloff.
- Icon tile at top-left.
- Sora H3 `20/28px`, ExtraBold.
- One-sentence explanation.
- Uppercase exploration link at the bottom.
- Hover: lift slightly and strengthen border/shadow.

### 10.7 Information note

- Wide mist/ice bar beneath use-case cards.
- Rounded `14–16px`.
- Compact `12–13px` copy.
- Bold domain names inline.
- No large icon required.

### 10.8 Design principles panel

- Large pale green-gray rounded panel.
- Eyebrow at upper-left.
- Heading and two paragraphs on the left.
- Pill action button aligned to the right at desktop.
- Stack action below copy on mobile.
- Panel radius `20–22px` and subtle border.

### 10.9 Eligibility mini-card

- Six cards on desktop, one column on mobile, two columns at intermediate widths.
- White fill, `14–16px` radius, subtle shadow.
- Small circular or rounded pale-green icon tile.
- Compact title and one- or two-line description.
- Keep icon/title alignment consistent.

### 10.10 Team structure panel

- Centered section heading.
- One wide pale panel below.
- Bold first sentence; remaining explanation is normal weight.
- Use one uninterrupted panel instead of separate member cards.

### 10.11 Prize feature

- Mist section with centered heading.
- Asymmetric desktop grid.
- Left prize card uses a rich forest-green gradient or layered deep-green surface.
- Prize amount is large, bold, and orange `#F5A623`.
- Trophy icon and faint oversized trophy outline reinforce the reward theme.
- Benefits are separated by low-opacity horizontal rules.
- Right side contains white recognition and finale cards.
- Finale card includes date, location, and institutional emblem.
- Mobile: stack prize card, recognition card, and finale card.

### 10.12 Footer

- Deep forest ink background `#0A2118`.
- White partner-logo pill on the left.
- Compact navigation and contact links in the center.
- Powered-by mark on the right.
- Thin low-opacity white divider.
- Copyright line uses very small muted white text.
- Mobile wraps content into several centered or left-aligned rows without shrinking logos excessively.

### 10.13 Cookie notice

- Floating white pill near the bottom center.
- Compact statement, neutral decline action, green filled accept action.
- Soft shadow and thin border.
- Mobile notice spans most of the width while leaving safe margins.
- It must not permanently obscure the primary CTA or navigation.

## 11. Section-by-section page recipe

1. **Institutional header** — partner lockup, navigation, register CTA.
2. **Doctor × technologist hero** — photographic collaboration, audience pairing, two role-specific CTAs.
3. **Joint initiative** — centered heading, five institutional partner cards, technology partner, powered-by lockup.
4. **Scope statement** — light/dark-green split explaining what the event is and is not.
5. **Use cases** — two large action cards plus a cross-domain note.
6. **Design bar** — pale green rounded panel documenting principles and out-of-scope work.
7. **Eligibility** — six compact audience cards on mist.
8. **Team structure** — one concise explanatory panel on white.
9. **Rewards and recognition** — forest-green prize panel plus benefits and finale details.
10. **Closing statement** — short impact line, invitation, and CTA.
11. **Institutional footer** — logos, navigation, contacts, powered-by attribution.

## 12. Alignment rules

- Use a consistent `1220px` desktop container.
- Align hero copy, section edges, and card grids to the same left axis.
- Center only major transition headings and their overlines.
- Keep partner-card logos top-aligned and partner links bottom-aligned.
- In two-column sections, align card tops and match corner radii.
- Keep icon tiles in a consistent first column in list components.
- Preserve generous space around institutional logos; never stretch or tint them.
- In the prize section, align the left prize card height with the stacked right-side group.

## 13. Responsive behavior

### At 390px

- Document content width: about `375px` after scrollbar.
- Main inner width: `335px` with `20px` side margins.
- Desktop hero photo is hidden and replaced by an image-first mobile composition.
- Header navigation collapses to a circular hamburger.
- Gradient event name: `31.2px / 30.576px`.
- Five partner cards stack into one column with `12px` gaps.
- Scope split stacks into one column with `16px` gap.
- Use-case cards stack with `20px` gap.
- Six eligibility cards stack with `12px` gaps.
- Rewards grid stacks with `20px` gaps.
- CTAs become full-width pills.
- Footer contacts wrap across multiple rows.

### At 640px and above

- Partner and eligibility grids may become two columns.
- Use-case cards become two columns only when each has enough space for readable copy.
- Keep the hero image-first or compact split until the desktop breakpoint.

### At 1024px and above

- Restore desktop navigation and register CTA.
- Restore text-left/image-right hero.
- Use five partner columns.
- Use two use-case columns.
- Use six eligibility columns.
- Use asymmetric rewards layout.

### Overflow rules

- No intentional horizontal page overflow.
- Logo lockup must scale inside its container without clipping.
- Long medical organization names wrap naturally.
- Do not reduce body copy below `12px` just to preserve a grid.

## 14. Motion and interaction

Observed transition families:

- `150ms` for colors, border, opacity, and small transforms.
- `200ms` for shadows.
- `220ms` for card transform, border, shadow, and background using `cubic-bezier(.2,.8,.2,1)`.
- `300ms` for expanded interactive states.

Recommended motion:

- Cards lift `2–3px` on hover.
- CTA arrow moves `2–4px` to the right.
- Button shadow strengthens slightly on hover.
- Partner links shift color only; do not animate logos.
- Use-case cards may reveal more detail in a modal/drawer, with accessible focus management.
- Hamburger opens a clean sheet or dropdown; avoid full-screen dramatic transitions.
- Honor `prefers-reduced-motion`.

## 15. Accessibility and medical-content guardrails

- Maintain WCAG AA contrast.
- Use at least `44px` touch targets.
- Preserve semantic heading order even when the visual hero uses styled spans.
- Provide useful alt text for partner logos and the hero photograph.
- Decorative background icons use empty alt text or `aria-hidden="true"`.
- Interactive use-case cards must be real buttons or links.
- Provide keyboard focus rings in clinical green with sufficient contrast.
- Do not communicate clinical categories with color alone.
- Never imply diagnosis, treatment recommendations, risk scoring, or autonomous clinical advice unless the product is appropriately validated and the content is legally reviewed.
- Pair any medical scope statement with clear human oversight and source attribution.
- Cookie controls must be keyboard accessible and equally understandable.

## 16. Content style

- Lead with the collaboration model and concrete audience.
- Use plain language for healthcare workflows.
- Distinguish clearly between clinical and non-clinical scope.
- Prefer measurable language: users, institutions, timeline, eligibility, outcomes.
- Use short proof-oriented partner descriptions.
- Keep CTA wording role-specific when different audiences have different flows.
- Use exact date and location metadata near the finale card.

## 17. Copy-ready master prompt

Replace the bracketed values and give this file to the implementation assistant.

```text
Design and implement [PROJECT NAME], a responsive [HEALTHCARE / RESEARCH / SOCIAL-IMPACT PAGE TYPE], using the attached Health-a-thon design blueprint (green edition) as the visual authority.

Create an original result for [AUDIENCE AND PURPOSE]. Do not copy Health-a-thon partner logos, photography, brand names, event copy, or medical claims. Use original or licensed assets.

VISUAL DIRECTION
- Calm, credible healthcare innovation aesthetic.
- White and pale green-gray surfaces, deep forest-ink text, clinical-green controls.
- Use one restrained green-to-teal gradient only for the project identity.
- Use real human collaboration photography rather than abstract AI imagery.
- Combine Sora ExtraBold headings with Inter body text.
- Use rounded white cards, thin forest-ink borders, subtle green-tinted shadows, compact spacing, and information-rich sections.

COLOR TOKENS
- Ink #0A2118
- Primary green #0C5C3F
- Muted text #4D6259
- Mist #F3F7F5
- Ice #E6F2EC
- Border rgba(10,33,24,.10)
- Identity gradient #0B7A3E → #12925A → #0F6F6A
- Reward accent #F5A623
- Domain accents stay non-green: cancer #1A5F9E, diabetes #A15C00, maternal #B83280

TYPOGRAPHY
- Display: Sora, 800, slightly negative tracking.
- Body and controls: Inter, 400–700.
- Hero audience title: clamp(36px, 5vw, 58.4px), line-height about 1.08.
- Main section H2: clamp(28px, 3vw, 37.6px), line-height about 1.16.
- Body: 16/24px; small copy 13–14/19.5–21px.

LAYOUT
- 1220px max container.
- Desktop horizontal inset about 50px at 1280px.
- Mobile inset 20px with approximately 335px inner width at 390px viewport.
- Compact section padding between 24px and 48px, not oversized marketing whitespace.
- Breakpoints at 640px and 1024px.

PAGE STRUCTURE
1. White institutional header with logo/partner lockup, compact navigation, and green pill CTA.
2. Split hero: copy left and collaborative healthcare photograph right. On mobile, place the image first and stack copy below it.
3. Mist partner ecosystem section with a centered heading, proof cards, and a technology partner strip.
4. White scope section with a light statement card and a dark forest-green checklist card.
5. Two large white use-case cards with icons, concise descriptions, and explore actions.
6. Pale green design-principles panel with explicit in-scope and out-of-scope language.
7. Mist eligibility grid for participant categories.
8. White team-structure section with one wide explanatory panel.
9. Mist rewards section with a forest-green prize card, white recognition card, and finale/location card.
10. Compact impact CTA and dark forest institutional footer.

COMPONENTS
- Primary CTA: green pill, white text, 43–52px high, subtle green shadow.
- Secondary CTA: white/transparent pill with 1.5px forest-ink border at 22% opacity.
- Standard card radius 16px; use-case radius 24px; feature panel 20–22px.
- Default card shadow: 0 1px 2px rgba(10,33,24,.06), 0 16px 40px -20px rgba(10,33,24,.24).
- Small pale-green icon tiles; consistent icon stroke and alignment.

IMAGERY
- Use authentic clinical/technical collaboration imagery in bright neutral spaces.
- Desktop hero image object-position around 62% 40% with soft white edge fades.
- Mobile hero uses a standalone landscape image before the headline.
- Keep all institutional logos undistorted, untinted, and surrounded by whitespace.

RESPONSIVE
- Desktop grids: five partners, two use cases, six eligibility cards, asymmetric rewards.
- Intermediate widths: two-column cards where readable.
- Mobile: single-column grids, image-first hero, full-width role CTAs, hamburger navigation.
- Never create page-level horizontal overflow.

MOTION
- 150ms color/opacity transitions, 200–220ms card and shadow transitions, max 300ms for expanded states.
- Card lift no more than 3px; arrow shift no more than 4px.
- Respect reduced motion.

ACCESSIBILITY AND SAFETY
- Semantic landmarks and heading hierarchy.
- WCAG AA contrast and visible green focus states.
- 44px minimum controls, useful alt text, keyboard-accessible cards and menu.
- Categories use text as well as color.
- Keep health solutions assistive and human-supervised; do not introduce unsupported diagnostic or treatment claims.

CONTENT AND FUNCTIONAL REQUIREMENTS
[PASTE NEW COPY, AUDIENCES, PARTNERS, USE CASES, ELIGIBILITY, REWARDS, CTA DESTINATIONS, AND ASSET LIST]

Deliver a production-ready implementation. Verify the full desktop layout, an intermediate tablet width, and a 390px mobile layout. Report intentional deviations from this blueprint.
```

## 18. Implementation tokens

```json
{
  "colors": {
    "ink": "#0A2118",
    "primary": "#0C5C3F",
    "muted": "#4D6259",
    "secondaryText": "#2A3D35",
    "white": "#FFFFFF",
    "mist": "#F3F7F5",
    "ice": "#E6F2EC",
    "border": "rgba(10,33,24,0.10)",
    "borderStrong": "rgba(10,33,24,0.22)",
    "paleGreen": "#A3D2BA",
    "gradientGreen": "#0B7A3E",
    "gradientEmerald": "#12925A",
    "gradientTeal": "#0F6F6A",
    "prize": "#F5A623",
    "cancer": "#1A5F9E",
    "diabetes": "#A15C00",
    "maternal": "#B83280"
  },
  "fonts": {
    "display": "Sora, Inter, system-ui, sans-serif",
    "body": "Inter, system-ui, sans-serif"
  },
  "radii": {
    "small": 10,
    "card": 16,
    "feature": 22,
    "useCase": 24,
    "pill": 9999
  },
  "shadows": {
    "card": "0 1px 2px rgba(10,33,24,.06), 0 16px 40px -20px rgba(10,33,24,.24)",
    "cta": "0 8px 22px rgba(12,92,63,.28)",
    "feature": "0 2px 4px rgba(10,33,24,.07), 0 32px 64px -24px rgba(10,33,24,.30)"
  },
  "container": {
    "max": 1220,
    "mobileInset": 20
  },
  "breakpoints": {
    "sm": 640,
    "lg": 1024
  },
  "motionMs": {
    "color": 150,
    "shadow": 200,
    "card": 220,
    "expanded": 300
  }
}
```

## 19. QA checklist

### Visual system

- [ ] Forest ink, clinical green, mist, and white retain their assigned roles.
- [ ] Identity gradient appears only in the main brand/title moment.
- [ ] Domain accents and prize orange stay non-green and are always paired with text.
- [ ] Sora is used for headings and Inter for body/controls.
- [ ] Negative heading tracking and compact line-height match the reference.
- [ ] Cards use 16–24px radii and restrained green-tinted shadows.
- [ ] Alternating white/mist sections create clear grouping.
- [ ] Institutional logos remain sharp, undistorted, and neutral.
- [ ] Hero photography emphasizes collaboration.

### Layout

- [ ] Desktop container does not exceed 1220px.
- [ ] Partner grid is five columns on desktop.
- [ ] Use cases are two columns on desktop.
- [ ] Eligibility is six columns on desktop.
- [ ] Rewards use an asymmetric desktop grid.
- [ ] Mobile inner width respects 20px margins.
- [ ] All grids become readable single-column stacks on mobile.
- [ ] No page-level horizontal overflow exists.

### Interaction

- [ ] Primary and secondary CTAs have clear hover/focus states.
- [ ] Use-case cards are keyboard operable.
- [ ] Mobile navigation is accessible and traps no focus.
- [ ] Cookie notice is accessible and does not block essential actions.
- [ ] Motion is subtle and reduced-motion safe.

### Accessibility and content

- [ ] Heading order is semantic despite custom hero styling.
- [ ] Contrast passes WCAG AA.
- [ ] Touch targets are at least 44px.
- [ ] Every meaningful image and logo has alt text.
- [ ] Domain categories are labelled in text.
- [ ] Medical scope and human oversight are explicit.
- [ ] No unsupported diagnostic or treatment claims appear.

## 20. Fidelity priorities

If implementation time is limited, preserve these in order:

1. Forest/mist/white color-role discipline.
2. Sora and Inter hierarchy.
3. Human collaboration hero composition.
4. Compact 1220px institutional grid.
5. Rounded white cards with restrained green-tinted shadows.
6. Partner ecosystem and role-specific CTAs.
7. White/mist section alternation.
8. Domain accents, prize treatment, and small interaction details.

The design remains recognizable with entirely original content and assets when these priorities are preserved.
