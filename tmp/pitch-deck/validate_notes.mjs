import { FileBlob, PresentationFile } from "@oai/artifact-tool";

const deckPath = "C:\\Users\\basit\\Downloads\\CODE\\healthathon\\pitch\\Continuity_Loop_Pitch_Deck.pptx";
const presentation = await PresentationFile.importPptx(await FileBlob.load(deckPath));
const inspected = await presentation.inspect({ kind: "notes", maxChars: 100000 });
const notesCount = (inspected.ndjson.match(/"kind":"notes"/g) || []).length;
const sourcesCount = (inspected.ndjson.match(/\[Sources\]/g) || []).length;
console.log(JSON.stringify({ notesCount, sourcesCount, hasVisualAsset: inspected.ndjson.includes("continuity-loop-healthathon.vercel.app") }));
