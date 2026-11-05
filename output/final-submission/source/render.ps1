param([string]$In,[string]$OutDir,[switch]$Pdf)
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
$pp=New-Object -ComObject PowerPoint.Application
$d=$pp.Presentations.Open($In,-1,0,0)
try{
  $d.Export($OutDir,'PNG',1600,900)
  if($Pdf){ $d.SaveAs(($In -replace '\.pptx$','.pdf'),32) }
  $rows=@()
  foreach($s in $d.Slides){ foreach($sh in $s.Shapes){ if($sh.HasTextFrame -eq -1 -and $sh.TextFrame.HasText -eq -1){
    $tf=$sh.TextFrame; $bh=$tf.TextRange.BoundHeight; $avail=$sh.Height-$tf.MarginTop-$tf.MarginBottom
    if($bh -gt $avail+1){ $rows+="OVERFLOW slide $($s.SlideIndex) $($sh.Name): text $([math]::Round($bh,1))pt > box $([math]::Round($avail,1))pt" }
    $bottom=$tf.TextRange.BoundTop+$bh
    if($bottom -gt $d.PageSetup.SlideHeight){ $rows+="OFFPAGE slide $($s.SlideIndex) $($sh.Name) bottom $([math]::Round($bottom,1))" }
  }}}
  "slides=$($d.Slides.Count)"; if($rows){$rows}else{'no overflow'}
} finally { $d.Close(); $pp.Quit() }
