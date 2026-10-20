const fs=require("node:fs");const path=require("node:path");const crypto=require("node:crypto");const assert=require("node:assert/strict");
const repo=path.resolve(__dirname,"../../../../..");const assets=path.join(repo,"prototype/public/vendor/tesseract");
const hash=f=>crypto.createHash("sha256").update(fs.readFileSync(f)).digest("hex");
(async()=>{let worker;try{
const {createWorker}=require(path.join(repo,"prototype/node_modules/tesseract.js"));
worker=await createWorker("eng",1,{workerPath:path.join(__dirname,"worker-local-core.cjs"),langPath:assets,corePath:assets,cacheMethod:"none",logger:()=>{}});
const image=path.join(__dirname,"prescription-synthetic.png");const result=await worker.recognize(image);const text=result.data.text;
fs.writeFileSync(path.join(__dirname,"actual-ocr.txt"),text);
const normalized=text.replace(/\s+/g," ").trim();
for(const word of ["SYNTHETIC TEST ONLY","EXAMPLE 500 mg","Dose: 1 tablet","Time: 08:00","Source page 1"])assert.ok(normalized.includes(word),`OCR missed ${word}`);
const metadata={checkedAtUtc:new Date().toISOString(),kind:"actual local Tesseract7 recognition with Node message adapter and identical browser WASM/language assets",syntheticOnly:true,networkBlockedInMainAndWorker:true,browserUiTested:false,nativeMlKitTested:false,fixtureSha256:hash(image),engineSha256:hash(path.join(assets,"tesseract-core-simd-lstm.wasm.js")),languageSha256:hash(path.join(assets,"eng.traineddata.gz")),confidence:result.data.confidence,actualText:text,assertionsPassed:5};
fs.writeFileSync(path.join(__dirname,"proof.json"),JSON.stringify(metadata,null,2)+"\n");console.log(JSON.stringify(metadata,null,2));
}catch(e){console.error(e);process.exitCode=1;}finally{if(worker)await worker.terminate();}})();
