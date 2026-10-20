const deny=()=>{throw new Error("NETWORK_BLOCKED_FOR_OFFLINE_OCR_PROOF");};
globalThis.fetch=async()=>deny();
for(const name of ["node:http","node:https"]){const mod=require(name);mod.request=deny;mod.get=deny;}
for(const name of ["node:net","node:tls"]){const mod=require(name);mod.connect=deny;mod.createConnection=deny;}
