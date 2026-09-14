# Rebuild more natural speaking.webm loops for CET personas.
# Source assets may be JPEG bytes even when named .png.
# @author liudy

$assets = "C:\Users\Administrator\.cursor\projects\e-java-workspace-ai-workspace-1-kidora-ai\assets"
$pub = "e:\java-workspace\ai_workspace_1\kidora-ai\kidora-web\public\personas"
$ff = "C:\ProgramData\chocolatey\bin\ffmpeg.exe"
$ids = @("emma", "mike", "lily", "tom", "coco", "alex")
$workRoot = Join-Path $env:TEMP "cet-speak-rebuild"
New-Item -ItemType Directory -Force -Path $workRoot | Out-Null

# 0=close, 1=soft, 2=open(speaking-half), 3=wide — irregular syllable cadence
$pattern = @(
  0,1,2,1,3,1,0,1,2,3,2,1,0,1,2,1,3,0,1,2,1,0,2,3,1,2,0,1,3,2,1,0
)

function Resolve-Mouth([string]$id, [int]$idx) {
  switch ($idx) {
    0 { return Join-Path $assets "$id-speak-close.png" }
    1 { return Join-Path $assets "$id-speak-soft.png" }
    2 { return Join-Path $assets "$id-speaking-half.png" }
    3 { return Join-Path $assets "$id-speak-wide.png" }
    default { return Join-Path $assets "$id-speak-soft.png" }
  }
}

foreach ($id in $ids) {
  Write-Host "building $id ..."
  $dir = Join-Path $workRoot $id
  if (Test-Path $dir) { Remove-Item -Recurse -Force $dir }
  New-Item -ItemType Directory -Force -Path $dir | Out-Null

  $i = 0
  foreach ($m in $pattern) {
    $src = Resolve-Mouth $id $m
    if (-not (Test-Path $src)) { throw "missing $src" }
    $dst = Join-Path $dir ("f{0:D3}.png" -f $i)
    $p = Start-Process -FilePath $ff -ArgumentList @(
      "-y","-i",$src,
      "-vf","scale=720:960:force_original_aspect_ratio=increase,crop=720:960",
      "-frames:v","1","-update","1",$dst
    ) -Wait -PassThru -NoNewWindow -RedirectStandardError "$env:TEMP\ff-norm-$id-$i.err"
    if ($p.ExitCode -ne 0 -or -not (Test-Path $dst)) {
      Get-Content "$env:TEMP\ff-norm-$id-$i.err" -ErrorAction SilentlyContinue | Select-Object -Last 8
      throw "normalize failed $src code=$($p.ExitCode)"
    }
    $i++
  }

  $outDir = Join-Path $pub $id
  New-Item -ItemType Directory -Force -Path $outDir | Out-Null
  $webm = Join-Path $outDir "speaking.webm"
  $poster = Join-Path $outDir "speaking.webp"
  $patternPath = (Join-Path $dir "f%03d.png")

  $vfSimple = "fps=20,zoompan=z='1.012+0.008*sin(2*PI*on/40)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)-2*sin(2*PI*on/36)':d=1:s=720x960:fps=20"
  $p = Start-Process -FilePath $ff -ArgumentList @(
    "-y","-framerate","10","-i",$patternPath,
    "-vf",$vfSimple,
    "-c:v","libvpx-vp9","-b:v","0","-crf","32","-an","-row-mt","1","-t","3.2",$webm
  ) -Wait -PassThru -NoNewWindow -RedirectStandardError "$env:TEMP\ff-webm-$id.err"
  if ($p.ExitCode -ne 0 -or -not (Test-Path $webm) -or ((Get-Item $webm).Length -lt 1000)) {
    Get-Content "$env:TEMP\ff-webm-$id.err" -ErrorAction SilentlyContinue | Select-Object -Last 12
    throw "ffmpeg webm failed for $id code=$($p.ExitCode)"
  }

  $soft = Join-Path $assets "$id-speak-soft.png"
  Start-Process -FilePath $ff -ArgumentList @(
    "-y","-i",$soft,
    "-vf","scale=720:960:force_original_aspect_ratio=increase,crop=720:960",
    "-frames:v","1","-c:v","libwebp","-quality","84",$poster
  ) -Wait -PassThru -NoNewWindow -RedirectStandardError "$env:TEMP\ff-poster-$id.err" | Out-Null

  $len = (Get-Item $webm).Length
  Write-Host ("ok {0} speaking.webm={1:N0}KB" -f $id, ($len / 1KB))
}

Write-Host "all speaking loops rebuilt"
