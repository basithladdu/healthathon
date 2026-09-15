import fs from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";

const REPO = "C:\\Users\\basit\\Downloads\\CODE\\rtih\\protohub";
const WORKSPACE = "C:\\tmp\\codex-presentations\\019f8ba7-protohub\\visual-final";
const starterPptxPath = path.join(WORKSPACE, "protohub-deck-starter.pptx");
const OUTPUT = path.join(REPO, "output", "protohub-deck-visual-final.pptx");
const ARTIFACT_TOOL = "C:\\Users\\basit\\.cache\\codex-runtimes\\codex-primary-runtime\\dependencies\\node\\node_modules\\@oai\\artifact-tool\\dist\\artifact_tool.mjs";

const HERO = path.join(REPO, "public", "assets", "protohub-hero.png");
const FACILITIES = path.join(REPO, "submission", "assets", "shots", "04_facilities_map.png");
const INNOVATOR_DASHBOARD = path.join(REPO, "submission", "assets", "shots", "01_dashboard.png");
const OPERATIONS = path.join(REPO, "output", "playwright", "expert-operations.png");

async function readImageBlob(imagePath) {
  const bytes = await fs.readFile(imagePath);
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
}

async function writeBlob(filePath, blob) {
  await fs.mkdir(path.dirname(filePath), { recursive: true });
  await fs.writeFile(filePath, new Uint8Array(await blob.arrayBuffer()));
}

function parseInspect(ndjson) {
  return String(ndjson || "")
    .split(/\r?\n/)
    .filter(Boolean)
    .map((line) => JSON.parse(line));
}

function findTextId(records, slide, startsWith) {
  const record = records.find(
    (item) =>
      item.slide === slide &&
      item.kind === "textbox" &&
      String(item.text || item.textPreview || "").startsWith(startsWith),
  );
  if (!record?.id) throw new Error(`Could not resolve slide ${slide} text starting with: ${startsWith}`);
  return record.id;
}

function setTextAndPosition(presentation, records, slide, startsWith, text, position) {
  const shape = presentation.resolve(findTextId(records, slide, startsWith));
  shape.text = text;
  shape.position = position;
}

function removeElement(presentation, id) {
  const element = presentation.resolve(id);
  if (typeof element.delete === "function") {
    element.delete();
    return;
  }
  if ("text" in element) element.text = "";
  element.position = { left: -20, top: -20, width: 1, height: 1 };
}

async function main() {
  const { FileBlob, PresentationFile } = await import(pathToFileURL(ARTIFACT_TOOL).href);
  const presentation = await PresentationFile.importPptx(await FileBlob.load(starterPptxPath));
  const slides = presentation.slides.items;
  const qaDir = path.join(WORKSPACE, "visual-edit-qa");
  const runtimeInspect = await presentation.inspect({
    kind: "slide,textbox,shape,image",
    maxChars: 200000,
  });
  const records = parseInspect(runtimeInspect.ndjson);

  await fs.mkdir(qaDir, { recursive: true });
  await fs.writeFile(path.join(qaDir, "before-inspect.ndjson"), runtimeInspect.ndjson || "", "utf8");
  for (let index = 0; index < slides.length; index += 1) {
    const slide = slides[index];
    await writeBlob(
      path.join(qaDir, `before-slide-${String(index + 1).padStart(2, "0")}.png`),
      await presentation.export({ slide, format: "png", scale: 2 }),
    );
    await writeBlob(
      path.join(qaDir, `before-slide-${String(index + 1).padStart(2, "0")}.layout.json`),
      await slide.export({ format: "layout" }),
    );
  }
  await writeBlob(
    path.join(qaDir, "before-montage.webp"),
    await presentation.export({ format: "webp", montage: true, scale: 1 }),
  );

  const slide1 = slides[0];
  setTextAndPosition(
    presentation,
    records,
    1,
    "A cloud Digital Operating System",
    "A cloud operating system for discovery, quotation, booking, production control and delivery tracking across APIC centres.",
    { left: 86.4, top: 422.4, width: 640, height: 124 },
  );
  slide1.images.add({
    blob: await readImageBlob(HERO),
    contentType: "image/png",
    alt: "ProtoHub challenge visual with the working platform shown on a laptop beside prototyping equipment",
    fit: "contain",
    position: { left: 830, top: 280, width: 380, height: 215 },
    geometry: "roundRect",
    borderRadius: 18,
  });

  const slide2 = slides[1];
  const problemRows = [
    ["APIC centres hold world-class machines", "APIC machines are capable, but discovery and booking are still manual.", 197.76],
    ["Innovators can't see what a prototype will cost", "Innovators cannot see cost or delivery time before they commit.", 279.36],
    ["Operations", "Operations data sits across disconnected files and spreadsheets.", 360.96],
    ["No real-time tracking", "There is no shared live view of jobs, machine load or utilisation.", 442.56],
    ["Result:", "Result: underused public assets and slow prototype turnaround.", 524.16],
  ];
  for (const [startsWith, text, top] of problemRows) {
    setTextAndPosition(
      presentation,
      records,
      2,
      startsWith,
      text,
      { left: 99.84, top, width: 625, height: 70 },
    );
  }
  slide2.images.add({
    blob: await readImageBlob(FACILITIES),
    contentType: "image/png",
    alt: "Working ProtoHub facilities map showing APIC centres across Andhra Pradesh",
    fit: "cover",
    crop: { left: 0.01, top: 0.06, right: 0.01, bottom: 0.08 },
    position: { left: 760, top: 184, width: 450, height: 370 },
    geometry: "roundRect",
    borderRadius: 16,
  });

  const slide3 = slides[2];
  setTextAndPosition(
    presentation,
    records,
    3,
    "Register + DPDP consent",
    "Guided intake, CAD upload and instant quote",
    { left: 121.92, top: 269.76, width: 500, height: 52.8 },
  );
  setTextAndPosition(
    presentation,
    records,
    3,
    "Submit + CAD upload",
    "Bookings, funding support and live tracking",
    { left: 121.92, top: 322.56, width: 500, height: 52.8 },
  );
  setTextAndPosition(
    presentation,
    records,
    3,
    "Multi-centre machine-load board",
    "Multi-centre machine load and project queue",
    { left: 717.12, top: 269.76, width: 480, height: 52.8 },
  );
  setTextAndPosition(
    presentation,
    records,
    3,
    "Project queue + Kanban workflow",
    "Inventory, maintenance, QA and billing",
    { left: 717.12, top: 322.56, width: 480, height: 52.8 },
  );

  [
    "DFM smart-routing:",
    "Automated GST quote",
    "Machine/expert booking",
    "Live tracking with delivery ETA",
    "Operator shifts",
    "Inventory, procurement",
    "Preventive maintenance",
    "Dispatch billing",
  ].forEach((startsWith) => removeElement(presentation, findTextId(records, 3, startsWith)));
  records
    .filter(
      (item) =>
        item.slide === 3 &&
        item.kind === "shape" &&
        Array.isArray(item.bbox) &&
        item.bbox[2] < 20 &&
        item.bbox[3] < 20 &&
        item.bbox[1] >= 387,
    )
    .forEach((item) => removeElement(presentation, item.id));

  slide3.images.add({
    blob: await readImageBlob(INNOVATOR_DASHBOARD),
    contentType: "image/png",
    alt: "Working ProtoHub innovator dashboard showing bookings, prototype actions and statewide activity",
    fit: "cover",
    crop: { left: 0.01, top: 0.03, right: 0.01, bottom: 0.2 },
    position: { left: 92, top: 382, width: 516, height: 204 },
    geometry: "roundRect",
    borderRadius: 12,
  });
  slide3.images.add({
    blob: await readImageBlob(OPERATIONS),
    contentType: "image/png",
    alt: "Working ProtoHub operations ERP showing facility queue, machine load and project status",
    fit: "cover",
    crop: { left: 0.01, top: 0.1, right: 0.01, bottom: 0.5 },
    position: { left: 686, top: 382, width: 500, height: 204 },
    geometry: "roundRect",
    borderRadius: 12,
  });

  for (let index = 0; index < slides.length; index += 1) {
    const slide = slides[index];
    await writeBlob(
      path.join(qaDir, `after-slide-${String(index + 1).padStart(2, "0")}.png`),
      await presentation.export({ slide, format: "png", scale: 2 }),
    );
    await writeBlob(
      path.join(qaDir, `after-slide-${String(index + 1).padStart(2, "0")}.layout.json`),
      await slide.export({ format: "layout" }),
    );
  }
  await writeBlob(
    path.join(qaDir, "after-montage.webp"),
    await presentation.export({ format: "webp", montage: true, scale: 1 }),
  );

  const after = await presentation.inspect({
    kind: "slide,textbox,shape,image,layout",
    maxChars: 200000,
  });
  await fs.writeFile(path.join(qaDir, "after-inspect.ndjson"), after.ndjson || "", "utf8");

  const pptx = await PresentationFile.exportPptx(presentation);
  await pptx.save(OUTPUT);
  console.log(OUTPUT);
}

main().catch((error) => {
  console.error(error.stack || error.message || String(error));
  process.exit(1);
});
