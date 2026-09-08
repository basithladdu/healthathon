import fs from "node:fs/promises";
import { Presentation, PresentationFile } from "@oai/artifact-tool";

const WIDTH = 1280;
const HEIGHT = 720;
const TMP_DIR = "C:\\Users\\basit\\Downloads\\CODE\\healthathon\\tmp\\pitch-deck";
const FINAL_PPTX = "C:\\Users\\basit\\Downloads\\CODE\\healthathon\\pitch\\Continuity_Loop_Pitch_Deck.pptx";
const SCREENSHOT = "C:\\Users\\basit\\Downloads\\CODE\\healthathon\\output\\playwright\\.playwright-cli\\page-2026-08-27T03-48-31-720Z.png";

const C = {
  canvas: "#FFFFFF",
  ink: "#101714",
  muted: "#53615D",
  faint: "#E9EEEC",
  panel: "#F2F5F4",
  rule: "#B8C4C0",
  accent: "#3D8DFF",
  accentLight: "#EAF3FF",
  green: "#0A6B57",
};

function position(left, top, width, height) {
  return { left, top, width, height };
}

function addText(slide, name, value, box, style = {}) {
  const shape = slide.shapes.add({
    geometry: "textbox",
    name,
    position: box,
    fill: "none",
    line: { style: "solid", fill: "none", width: 0 },
  });
  shape.text = value;
  shape.text.style = {
    fontSize: style.fontSize ?? 18,
    typeface: style.typeface ?? "Helvetica Neue",
    color: style.color ?? C.ink,
    bold: style.bold ?? false,
    italic: style.italic ?? false,
    alignment: style.alignment ?? "left",
    verticalAlignment: style.verticalAlignment ?? "top",
    autoFit: style.autoFit ?? "shrinkText",
    wrap: "square",
    insets: style.insets ?? { top: 0, right: 0, bottom: 0, left: 0 },
  };
  return shape;
}

function addBox(slide, name, box, fill, line = C.rule, radius = 0) {
  return slide.shapes.add({
    geometry: radius ? "roundRect" : "rect",
    name,
    position: box,
    fill,
    line: { style: "solid", fill: line, width: line === "none" ? 0 : 1 },
    ...(radius ? { borderRadius: radius } : {}),
  });
}

function addRule(slide, name, left, top, width, height = 2, fill = C.rule) {
  return addBox(slide, name, position(left, top, width, height), fill, "none");
}

function addCircle(slide, name, left, top, size, fill, line = fill) {
  return slide.shapes.add({
    geometry: "ellipse",
    name,
    position: position(left, top, size, size),
    fill,
    line: { style: "solid", fill: line, width: 1 },
  });
}

function footer(slide, number) {
  addRule(slide, `footer-rule-${number}`, 42, 666, 1196, 1, C.rule);
  addText(slide, `footer-label-${number}`, "CONTINUITY LOOP  ·  HEALTH-A-THON 2026", position(42, 678, 430, 20), {
    fontSize: 14,
    color: C.muted,
    bold: true,
  });
  addText(slide, `footer-number-${number}`, String(number).padStart(2, "0"), position(1170, 676, 68, 22), {
    fontSize: 16,
    color: C.ink,
    bold: true,
    alignment: "right",
  });
}

function notes(slide, talkTrack, sources) {
  slide.speakerNotes.textFrame.setText([
    talkTrack,
    "",
    "[Sources]",
    ...sources.map((source) => `- ${source}`),
  ]);
  slide.speakerNotes.setVisible(true);
}

function addBullet(slide, name, top, label, width = 540, color = C.ink, left = 42, markColor = C.accent) {
  addBox(slide, `${name}-mark`, position(left, top + 7, 10, 10), markColor, "none");
  addText(slide, `${name}-text`, label, position(left + 28, top, width, 48), {
    fontSize: 18,
    color,
  });
}

function addStep(slide, index, left, title, body) {
  addBox(slide, `step-${index}-rule`, position(left, 235, 206, 5), C.accent, "none");
  addText(slide, `step-${index}-number`, String(index).padStart(2, "0"), position(left, 254, 48, 26), {
    fontSize: 16,
    color: C.accent,
    bold: true,
  });
  addText(slide, `step-${index}-title`, title, position(left, 297, 206, 70), {
    fontSize: 22,
    bold: true,
  });
  addText(slide, `step-${index}-body`, body, position(left, 384, 206, 112), {
    fontSize: 16,
    color: C.muted,
  });
}

async function writeBlob(path, blob) {
  await fs.writeFile(path, new Uint8Array(await blob.arrayBuffer()));
}

async function main() {
  await fs.mkdir(TMP_DIR, { recursive: true });
  const presentation = Presentation.create({ slideSize: { width: WIDTH, height: HEIGHT } });
  const screenshotBytes = await fs.readFile(SCREENSHOT);

  // Slide 1: cover, following the sparse stacked-text cover silhouette.
  {
    const slide = presentation.slides.add();
    slide.background.fill = C.canvas;
    addText(slide, "cover-kicker", "CONTINUITY LOOP", position(42, 42, 340, 28), {
      fontSize: 16,
      color: C.green,
      bold: true,
    });
    addRule(slide, "cover-kicker-rule", 42, 82, 120, 4, C.accent);
    addText(slide, "cover-title", "Keep a clinician-owned\nconversation current.", position(42, 185, 930, 190), {
      fontSize: 68,
      bold: true,
    });
    addText(slide, "cover-subtitle", "Goals-of-care follow-up and verified clinical handoff for cancer care.", position(42, 414, 780, 70), {
      fontSize: 24,
      color: C.muted,
    });
    addText(slide, "cover-context", "CANCER CARE  /  PATIENT FOLLOW-UP & CONTINUITY OF CARE  /  HACKATHON MVP", position(42, 566, 960, 28), {
      fontSize: 16,
      color: C.ink,
      bold: true,
    });
    addBox(slide, "cover-accent-field", position(1092, 42, 146, 568), C.accentLight, "none");
    addText(slide, "cover-accent-copy", "CLINICIAN\nOWNED\nVERIFIED\nRETRIEVABLE", position(1112, 105, 118, 220), {
      fontSize: 17,
      color: C.accent,
      bold: true,
    });
    addText(slide, "cover-synthetic", "SYNTHETIC PROTOTYPE", position(1120, 548, 100, 35), {
      fontSize: 14,
      color: C.muted,
      bold: true,
    });
    footer(slide, 1);
    notes(slide, "Open with the continuity failure and the product's narrow promise: keep a clinician-owned conversation current and retrievable when care changes.", [
      "C:\\Users\\basit\\Downloads\\CODE\\healthathon\\VENTURE_DECISION_MATRIX.md (selected use case and product position)",
      "C:\\Users\\basit\\Downloads\\CODE\\healthathon\\MEETING_SUMMARY_FOR_TEAM.md (one-sentence concept)",
    ]);
  }

  // Slide 2: problem, following a two-column narrative/process silhouette.
  {
    const slide = presentation.slides.add();
    slide.background.fill = C.canvas;
    addText(slide, "problem-title", "The failure is continuity, not the absence of another note.", position(42, 36, 1196, 78), {
      fontSize: 40,
      bold: true,
    });
    addText(slide, "problem-intro", "A difficult conversation can be unfinished, informal, or hard to find when care changes.", position(42, 139, 540, 80), {
      fontSize: 22,
      color: C.muted,
    });
    addText(slide, "problem-statement", "At night, the receiving physician and family may repeat the conversation under pressure.", position(42, 315, 510, 130), {
      fontSize: 30,
      bold: true,
    });
    addText(slide, "problem-implication", "The operational wedge is not a document repository. It is a closed loop from follow-up ownership to verified retrieval.", position(42, 500, 520, 88), {
      fontSize: 18,
      color: C.muted,
    });
    addRule(slide, "problem-flow-rule", 704, 292, 442, 3, C.rule);
    const stages = [
      ["01", "Treating clinician", "Conversation may remain incomplete."],
      ["02", "Patient + family", "Preferences can change or need revisiting."],
      ["03", "Receiving physician", "The latest verified context must be findable."],
    ];
    stages.forEach(([number, title, body], index) => {
      const left = 690 + index * 178;
      addCircle(slide, `problem-node-${index}`, left, 275, 38, index === 2 ? C.accent : C.panel, index === 2 ? C.accent : C.rule);
      addText(slide, `problem-node-number-${index}`, number, position(left, 286, 38, 20), {
        fontSize: 14,
        bold: true,
        color: index === 2 ? C.canvas : C.ink,
        alignment: "center",
      });
      addText(slide, `problem-node-title-${index}`, title, position(left - 5, 350, 150, 52), {
        fontSize: 18,
        bold: true,
      });
      addText(slide, `problem-node-body-${index}`, body, position(left - 5, 419, 150, 95), {
        fontSize: 16,
        color: C.muted,
      });
    });
    footer(slide, 2);
    notes(slide, "Name the failure as a continuity problem: the conversation may exist somewhere, but it does not reliably travel with the patient's changing care context.", [
      "C:\\Users\\basit\\Downloads\\CODE\\healthathon\\MEETING_SUMMARY_FOR_TEAM.md (problem described by clinicians)",
      "C:\\Users\\basit\\Downloads\\CODE\\healthathon\\VENTURE_DECISION_MATRIX.md (visible handoff wedge and operational product)",
    ]);
  }

  // Slide 3: retrieval wedge with authentic product screenshot.
  {
    const slide = presentation.slides.add();
    slide.background.fill = C.canvas;
    addText(slide, "wedge-title", "The first high-trust moment is a verified handoff.", position(42, 36, 1196, 78), {
      fontSize: 40,
      bold: true,
    });
    addText(slide, "wedge-kicker", "RECEIVING PHYSICIAN", position(42, 164, 420, 24), {
      fontSize: 16,
      color: C.green,
      bold: true,
    });
    addText(slide, "wedge-claim", "Find the latest\nrecord—after\nrecording why.", position(42, 205, 455, 160), {
      fontSize: 36,
      bold: true,
    });
    addText(slide, "wedge-body", "The receiving clinician sees a controlled access gate before the verified summary is opened.", position(42, 410, 430, 72), {
      fontSize: 19,
      color: C.muted,
    });
    addBullet(slide, "wedge-bullet-1", 515, "Patient-bound record", 460);
    addBullet(slide, "wedge-bullet-2", 554, "Purpose + care relationship", 460);
    addBullet(slide, "wedge-bullet-3", 593, "Latest version + audit event", 460);
    addBox(slide, "wedge-image-frame", position(548, 145, 690, 480), C.panel, C.rule, 10);
    slide.images.add({
      name: "wedge-product-screenshot",
      blob: screenshotBytes.buffer.slice(screenshotBytes.byteOffset, screenshotBytes.byteOffset + screenshotBytes.byteLength),
      contentType: "image/png",
      alt: "Synthetic Continuity Loop emergency retrieval access gate",
      fit: "contain",
      geometry: "rect",
      position: position(565, 164, 656, 443),
    });
    addText(slide, "wedge-caption", "Production UI capture · synthetic data · emergency access gate", position(565, 612, 656, 22), {
      fontSize: 14,
      color: C.muted,
      alignment: "right",
    });
    footer(slide, 3);
    notes(slide, "Use the screenshot as the product proof point. The value is the controlled handoff gate: the clinician records the access purpose and relationship before retrieving the current verified record.", [
      "C:\\Users\\basit\\Downloads\\CODE\\healthathon\\PRODUCT_REQUIREMENTS.md (emergency retrieval requirements)",
      "C:\\Users\\basit\\Downloads\\CODE\\healthathon\\PROTOTYPE_HANDOFF.md (demonstrated retrieval workflow and boundaries)",
      "Visual asset: https://continuity-loop-healthathon.vercel.app/ (captured 27 August 2026 at 1440 x 1000 CSS pixels; synthetic data)",
    ]);
  }

  // Slide 4: workflow, following a process sequence silhouette.
  {
    const slide = presentation.slides.add();
    slide.background.fill = C.canvas;
    addText(slide, "workflow-title", "A closed loop connects follow-up, documentation, verification and retrieval.", position(42, 36, 1196, 78), {
      fontSize: 40,
      bold: true,
    });
    addText(slide, "workflow-intro", "Five clinician-owned states make the continuity handoff explicit.", position(42, 137, 760, 36), {
      fontSize: 22,
      color: C.muted,
    });
    addRule(slide, "workflow-spine", 146, 219, 990, 2, C.rule);
    [146, 374, 602, 830, 1058].forEach((left, index) => addCircle(slide, `workflow-spine-node-${index}`, left, 207, 26, C.canvas, C.accent));
    addStep(slide, 1, 42, "Enrol + assign", "The clinician selects the patient, owner and next review date.");
    addStep(slide, 2, 270, "Talk + mark", "The clinician records discussed, not discussed or defer/clarify.");
    addStep(slide, 3, 498, "Draft + trace", "Each substantive field stays linked to the source conversation.");
    addStep(slide, 4, 726, "Review + acknowledge", "The physician edits, resolves and releases the verified version.");
    addStep(slide, 5, 954, "Retrieve + reconfirm", "The receiving physician records purpose and checks what may have changed.");
    addBox(slide, "workflow-version-box", position(42, 560, 1196, 54), C.accentLight, "none", 4);
    addText(slide, "workflow-version-text", "A changed preference creates a new version; nothing released is overwritten.", position(64, 575, 1135, 24), {
      fontSize: 20,
      bold: true,
      color: C.ink,
    });
    footer(slide, 4);
    notes(slide, "Walk left to right. The worklist is the operational layer, but the record is only released after the physician review and acknowledgement gate; retrieval ends with reconfirmation, not an automatic order.", [
      "C:\\Users\\basit\\Downloads\\CODE\\healthathon\\PRODUCT_REQUIREMENTS.md (P0 workflow, verification, versioning and retrieval requirements)",
      "C:\\Users\\basit\\Downloads\\CODE\\healthathon\\PROTOTYPE_HANDOFF.md (demonstrated workflow)",
    ]);
  }

  // Slide 5: provenance and human gate, following an evidence-plus-interpretation silhouette.
  {
    const slide = presentation.slides.add();
    slide.background.fill = C.canvas;
    addText(slide, "trust-title", "Every field has a source. Every release has a human gate.", position(42, 36, 1196, 78), {
      fontSize: 40,
      bold: true,
    });
    addText(slide, "trust-source-label", "SYNTHETIC EXCERPT", position(42, 148, 500, 24), {
      fontSize: 16,
      color: C.green,
      bold: true,
    });
    addBox(slide, "trust-source-box", position(42, 185, 510, 220), C.panel, C.rule, 4);
    addText(slide, "trust-source-copy", "“Patient initiated discussion\nregarding family understanding\nof illness.”", position(75, 235, 440, 130), {
      fontSize: 25,
      italic: true,
      bold: true,
    });
    addText(slide, "trust-source-meta", "Source-linked draft · synthetic patient", position(75, 370, 430, 24), {
      fontSize: 16,
      color: C.muted,
    });
    addRule(slide, "trust-connector", 552, 292, 118, 3, C.accent);
    addText(slide, "trust-connector-label", "maps to", position(570, 260, 90, 22), {
      fontSize: 14,
      color: C.accent,
      bold: true,
      alignment: "center",
    });
    addText(slide, "trust-field-label", "STRUCTURED FIELD", position(710, 148, 500, 24), {
      fontSize: 16,
      color: C.green,
      bold: true,
    });
    addBox(slide, "trust-field-box", position(710, 185, 528, 145), C.accentLight, C.accent, 4);
    addText(slide, "trust-field-copy", "Patient priority", position(740, 215, 460, 28), {
      fontSize: 22,
      bold: true,
    });
    addText(slide, "trust-field-value", "Patient initiated discussion regarding family understanding of illness.", position(740, 260, 450, 52), {
      fontSize: 18,
      color: C.ink,
    });
    addText(slide, "trust-gate-label", "RELEASE GATE", position(710, 366, 500, 24), {
      fontSize: 16,
      color: C.green,
      bold: true,
    });
    addBullet(slide, "trust-bullet-1", 405, "Unstated stays unstated.", 500, C.ink, 710);
    addBullet(slide, "trust-bullet-2", 452, "Clarification blocks publication.", 500, C.ink, 710);
    addBullet(slide, "trust-bullet-3", 499, "Physician review and acknowledgement are explicit.", 500, C.ink, 710);
    addBox(slide, "trust-bottom", position(42, 571, 1196, 45), C.panel, "none", 4);
    addText(slide, "trust-bottom-text", "Deterministic prototype behaviour · no external AI model is active", position(64, 584, 1140, 22), {
      fontSize: 17,
      color: C.muted,
      bold: true,
    });
    footer(slide, 5);
    notes(slide, "Show the trust mechanism, not an AI magic trick. The source excerpt anchors the field; unsupported silence remains visible, and the physician gate is what makes release possible.", [
      "C:\\Users\\basit\\Downloads\\CODE\\healthathon\\PRODUCT_REQUIREMENTS.md (source mapping, unresolved fields and verification gate)",
      "C:\\Users\\basit\\Downloads\\CODE\\healthathon\\PROTOTYPE_HANDOFF.md (deterministic extraction and no external AI claim)",
    ]);
  }

  // Slide 6: truth boundary, following a restrained two-column composition.
  {
    const slide = presentation.slides.add();
    slide.background.fill = C.canvas;
    addText(slide, "boundary-title", "The MVP is useful because its boundaries are explicit.", position(42, 36, 1196, 78), {
      fontSize: 40,
      bold: true,
    });
    addText(slide, "boundary-now-label", "DEMONSTRATED NOW", position(42, 155, 500, 24), {
      fontSize: 16,
      color: C.green,
      bold: true,
    });
    addText(slide, "boundary-not-label", "NOT CLAIMED", position(694, 155, 500, 24), {
      fontSize: 16,
      color: C.green,
      bold: true,
    });
    addRule(slide, "boundary-divider", 639, 150, 1, 410, C.rule);
    const demonstrated = [
      "Synthetic patient binding",
      "Guided eight-domain checklist",
      "Deterministic source-linked draft",
      "Physician review + acknowledgement gate",
      "Append-only version 2",
      "Purpose-bound retrieval + audit",
      "Coordinator outreach worklist",
    ];
    demonstrated.forEach((item, index) => addBullet(slide, `boundary-now-${index}`, 201 + index * 44, item, 530));
    const notClaimed = [
      "Live AI transcription or model accuracy",
      "Real authentication, encryption or cryptographic audit",
      "EHR, ABDM or messaging integration",
      "Patient outcomes or treatment effects",
      "Legal signature, living will or treatment order",
      "Autonomous triage, prognosis or recommendation",
    ];
    notClaimed.forEach((item, index) => {
      addBox(slide, `boundary-not-${index}-mark`, position(694, 208 + index * 50, 10, 10), C.rule, "none");
      addText(slide, `boundary-not-${index}-text`, item, position(722, 201 + index * 50, 485, 40), {
        fontSize: 18,
        color: C.ink,
      });
    });
    addBox(slide, "boundary-bottom", position(42, 586, 1196, 38), C.accentLight, "none", 4);
    addText(slide, "boundary-bottom-text", "Clinical summary only · Reconfirm current status before acute orders.", position(64, 595, 1144, 20), {
      fontSize: 17,
      bold: true,
    });
    footer(slide, 6);
    notes(slide, "Say the exclusions plainly. These are not missing features to hide; they are the safety boundary of the hackathon prototype and the reason the current proof can be evaluated honestly.", [
      "C:\\Users\\basit\\Downloads\\CODE\\healthathon\\PRODUCT_REQUIREMENTS.md (demonstration integrity and non-goals)",
      "C:\\Users\\basit\\Downloads\\CODE\\healthathon\\PROTOTYPE_HANDOFF.md (current implementation boundary)",
      "C:\\Users\\basit\\Downloads\\CODE\\healthathon\\MEETING_SUMMARY_FOR_TEAM.md (clinical summary, not legal/treatment authority)",
    ]);
  }

  // Slide 7: validation, following the metric-led silhouette without presenting outcomes as facts.
  {
    const slide = presentation.slides.add();
    slide.background.fill = C.canvas;
    addText(slide, "validation-title", "The next credible proof is a clinician-reviewed simulation.", position(42, 36, 1196, 78), {
      fontSize: 40,
      bold: true,
    });
    addText(slide, "validation-intro", "Compare product output with clinician-authored references across varied synthetic or fully anonymised scenarios.", position(42, 137, 1010, 45), {
      fontSize: 22,
      color: C.muted,
    });
    const metrics = [
      ["20", "scenarios to run", "A clinician-owned reference set."],
      ["8", "conversation domains", "The current prototype checklist."],
      ["<30 sec", "retrieval target", "A target, not an observed result."],
      ["0", "unstated preferences filled", "An integrity invariant."],
    ];
    metrics.forEach(([number, title, body], index) => {
      const left = 42 + index * 300;
      addText(slide, `metric-${index}-number`, number, position(left, 236, 250, 80), {
        fontSize: 48,
        color: C.accent,
        bold: true,
      });
      addRule(slide, `metric-${index}-rule`, left, 322, 238, 4, C.accent);
      addText(slide, `metric-${index}-title`, title, position(left, 343, 250, 46), {
        fontSize: 20,
        bold: true,
      });
      addText(slide, `metric-${index}-body`, body, position(left, 399, 250, 62), {
        fontSize: 16,
        color: C.muted,
      });
    });
    addText(slide, "validation-measures-label", "MEASURE", position(42, 508, 160, 24), {
      fontSize: 16,
      color: C.green,
      bold: true,
    });
    addText(slide, "validation-measures", "Required-domain coverage · unsupported statement rate · unresolved-item capture · clinician correction rate · time to prepare/retrieve · receiving-clinician clarity", position(190, 507, 1015, 55), {
      fontSize: 18,
      color: C.ink,
    });
    addText(slide, "validation-footnote", "Targets / plan — not observed outcomes.", position(42, 585, 500, 24), {
      fontSize: 16,
      color: C.muted,
      italic: true,
    });
    footer(slide, 7);
    notes(slide, "Make the next step concrete. The 20-case exercise is a proposed validation plan; it is not a result. Compare the product record to a clinician-authored reference and measure fidelity, uncertainty capture and retrieval clarity.", [
      "C:\\Users\\basit\\Downloads\\CODE\\healthathon\\VENTURE_DECISION_MATRIX.md (20-case pilot hypothesis and measures)",
      "C:\\Users\\basit\\Downloads\\CODE\\healthathon\\PRODUCT_REQUIREMENTS.md (eight-domain checklist and no-inference requirement)",
      "C:\\Users\\basit\\Downloads\\CODE\\healthathon\\PROTOTYPE_HANDOFF.md (current MVP scorecard boundaries)",
    ]);
  }

  // Slide 8: close, following a sparse stacked-text close silhouette.
  {
    const slide = presentation.slides.add();
    slide.background.fill = C.canvas;
    addText(slide, "ask-kicker", "THE NEXT DECISION", position(42, 42, 300, 28), {
      fontSize: 16,
      color: C.green,
      bold: true,
    });
    addRule(slide, "ask-kicker-rule", 42, 82, 120, 4, C.accent);
    addText(slide, "ask-title", "The decision needed now\nis clinical ownership.", position(42, 150, 580, 150), {
      fontSize: 52,
      bold: true,
    });
    addText(slide, "ask-intro", "To make this a real pilot, we need four inputs from clinicians.", position(42, 330, 530, 58), {
      fontSize: 22,
      color: C.muted,
    });
    const asks = [
      ["01", "Approve the exact conversation domains and wording."],
      ["02", "Provide 20 synthetic scenarios and clinician reference records."],
      ["03", "Name one workflow owner, pilot setting and due-date rule."],
      ["04", "Define acknowledgement, surrogate authority and retrieval roles."],
    ];
    asks.forEach(([number, text], index) => {
      const top = 157 + index * 104;
      addText(slide, `ask-number-${index}`, number, position(710, top, 50, 24), {
        fontSize: 16,
        color: C.accent,
        bold: true,
      });
      addText(slide, `ask-copy-${index}`, text, position(770, top - 2, 430, 65), {
        fontSize: 20,
        bold: true,
      });
      addRule(slide, `ask-rule-${index}`, 710, top + 74, 490, 1, C.rule);
    });
    addBox(slide, "ask-boundary", position(42, 525, 1196, 66), C.accentLight, "none", 4);
    addText(slide, "ask-boundary-copy", "Documents, verifies and retrieves clinician decisions.\nIt does not select patients, recommend treatment or create a legal directive.", position(64, 539, 1135, 44), {
      fontSize: 20,
      bold: true,
    });
    addText(slide, "ask-url", "continuity-loop-healthathon.vercel.app  ·  synthetic prototype  ·  clinical review pending", position(42, 625, 930, 22), {
      fontSize: 16,
      color: C.muted,
    });
    footer(slide, 8);
    notes(slide, "Close with a decision, not a generic thank-you. The product is ready for clinician-owned validation, but the clinical protocol, reference cases, pilot owner and access semantics still need explicit agreement.", [
      "C:\\Users\\basit\\Downloads\\CODE\\healthathon\\TOMORROW_CALL_CARD.md (clinician decision gate and required inputs)",
      "C:\\Users\\basit\\Downloads\\CODE\\healthathon\\VENTURE_DECISION_MATRIX.md (remaining clinical decisions and pilot hypothesis)",
      "C:\\Users\\basit\\Downloads\\CODE\\healthathon\\PRODUCT_REQUIREMENTS.md (P1 clinician protocol requirements)",
    ]);
  }

  for (const [index, slide] of presentation.slides.items.entries()) {
    const stem = `slide-${String(index + 1).padStart(2, "0")}`;
    await writeBlob(`${TMP_DIR}/${stem}.png`, await presentation.export({ slide, format: "png", scale: 1 }));
    await fs.writeFile(`${TMP_DIR}/${stem}.layout.json`, await (await slide.export({ format: "layout" })).text());
  }
  await writeBlob(`${TMP_DIR}/deck-montage.webp`, await presentation.export({ format: "webp", montage: true, scale: 1 }));
  await fs.writeFile(`${TMP_DIR}/inspect.ndjson`, (await presentation.inspect({ kind: "slide,textbox,shape,image,notes", maxChars: 20000 })).ndjson);
  const pptx = await PresentationFile.exportPptx(presentation);
  await pptx.save(FINAL_PPTX);
  console.log(`Created ${FINAL_PPTX}`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
