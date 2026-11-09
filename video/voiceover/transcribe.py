import os,json,sys
os.environ["HF_HOME"]="E:/Healthathon/sahara-video/voiceover/hf"
import site,glob
for p in glob.glob(os.path.join(sys.prefix,"Lib","site-packages","nvidia","*","bin")): os.add_dll_directory(p); os.environ["PATH"]=p+os.pathsep+os.environ["PATH"]
from faster_whisper import WhisperModel
try:
    raise Exception("skip gpu: cudnn download failed"); m=WhisperModel("medium.en",device="cuda",compute_type="float16",download_root="E:/Healthathon/sahara-video/voiceover/hf"); dev="cuda"
except Exception as e:
    print("cuda fail",e); m=WhisperModel("medium.en",device="cpu",compute_type="int8",download_root="E:/Healthathon/sahara-video/voiceover/hf"); dev="cpu"
segs,_=m.transcribe("elevenlabs.mp3",word_timestamps=True,language="en",beam_size=5,condition_on_previous_text=False)
w=[dict(w=x.word,s=x.start,e=x.end,p=x.probability) for s in segs for x in s.words]
json.dump(w,open("words.json","w"),indent=0); print(dev,len(w))
