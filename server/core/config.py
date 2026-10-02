import os

PROJECT = "/content/drive/MyDrive/ai-toolkit"
WORK = "/content/kerja"          # disk lokal (cepat)
OUTPUT = f"{PROJECT}/output"     # hasil disalin ke Drive
os.makedirs(WORK, exist_ok=True)
os.makedirs(OUTPUT, exist_ok=True)

VIDEO_FMT = ["mp4", "mkv", "mov", "avi", "webm", "flv", "wmv", "m4v", "ts", "3gp", "mpg", "gif"]
AUDIO_FMT = ["mp3", "wav", "opus", "ogg", "flac", "aac", "m4a", "wma", "aiff", "ac3"]
IMAGE_FMT = ["jpg", "jpeg", "png", "webp", "bmp", "tiff", "ico"]

_H264 = ["-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-c:a", "aac", "-pix_fmt", "yuv420p"]

CODEC = {
    # ---- VIDEO ----
    "mp4":  _H264,
    "mkv":  ["-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-c:a", "aac"],
    "mov":  _H264,
    "webm": ["-c:v", "libvpx-vp9", "-deadline", "realtime", "-cpu-used", "8",
             "-row-mt", "1", "-crf", "33", "-b:v", "0", "-c:a", "libopus"],
    "avi":  ["-c:v", "mpeg4", "-c:a", "mp3"],
    "flv":  ["-c:v", "flv", "-c:a", "mp3"],
    "wmv":  ["-c:v", "wmv2", "-c:a", "wmav2"],
    "m4v":  _H264,
    "ts":   _H264,
    "3gp":  _H264,
    "mpg":  ["-c:v", "mpeg2video", "-q:v", "4", "-c:a", "mp2"],
    "gif":  ["-vf", "fps=12,scale='min(480,iw)':-2:flags=lanczos", "-an"],

    # ---- AUDIO ----
    "mp3":  ["-c:a", "libmp3lame", "-q:a", "2"],
    "wav":  ["-c:a", "pcm_s16le"],
    "opus": ["-c:a", "libopus", "-b:a", "96k"],
    "ogg":  ["-c:a", "libvorbis", "-q:a", "5"],
    "flac": ["-c:a", "flac"],
    "aac":  ["-c:a", "aac", "-b:a", "192k"],
    "m4a":  ["-c:a", "aac", "-b:a", "192k"],
    "wma":  ["-c:a", "wmav2", "-b:a", "192k"],
    "aiff": ["-c:a", "pcm_s16be"],
    "ac3":  ["-c:a", "ac3", "-b:a", "192k"],

    # ---- GAMBAR ----
    "jpg":  ["-c:v", "mjpeg", "-q:v", "2"],
    "jpeg": ["-c:v", "mjpeg", "-q:v", "2"],
    "png":  ["-c:v", "png"],
    "webp": ["-c:v", "libwebp", "-q:v", "80"],
    "bmp":  ["-c:v", "bmp"],
    "tiff": ["-c:v", "tiff"],
    "ico":  ["-vf", "scale='min(256,iw)':'min(256,ih)':force_original_aspect_ratio=decrease"],
}

# Container yang boleh diisi H.264 + AAC tanpa re-encode
REMUX_OK = {"mp4", "mkv", "mov", "m4v", "ts"}

# Pengaman: semua format di dropdown WAJIB punya setting di CODEC
_semua = set(VIDEO_FMT + AUDIO_FMT + IMAGE_FMT)
_kurang = sorted(_semua - set(CODEC))
if _kurang:
    raise ValueError(f"Format belum punya setting di CODEC: {_kurang}")
