const path=require("node:path");
const repo=path.resolve(__dirname,"../../../../..");
const nodeGetCore=path.join(repo,"prototype/node_modules/tesseract.js/src/worker-script/node/getCore.js");
const core=require(path.join(__dirname,"browser-core-identical.cjs"));
require.cache[require.resolve(nodeGetCore)]={id:nodeGetCore,filename:nodeGetCore,loaded:true,exports:async()=>core};
require(path.join(repo,"prototype/node_modules/tesseract.js/src/worker-script/node/index.js"));
