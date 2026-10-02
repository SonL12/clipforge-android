import subprocess, json, tempfile, threading, time
from core.config import VIDEO_FMT, AUDIO_FMT, IMAGE_FMT


class Dibatalkan(Exception):
    pass


_ctx = threading.local()


def set_cancel_event(ev):
    """Dipanggil server: event ini menghentikan ffmpeg yang jalan di thread ini."""
    _ctx.cancel = ev


def jenis_file(ext):
    ext = ext.lower().lstrip(".")
    if ext in VIDEO_FMT: return "video"
    if ext in AUDIO_FMT: return "audio"
    if ext in IMAGE_FMT: return "image"
    return None


def run_ffmpeg(cmd):
    """Jalankan ffmpeg. Gagal -> RuntimeError, dibatalkan -> Dibatalkan."""
    cancel = getattr(_ctx, "cancel", None)
    with tempfile.TemporaryFile(mode="w+") as err:
        p = subprocess.Popen(cmd, stdin=subprocess.DEVNULL,
                             stdout=subprocess.DEVNULL, stderr=err)
        while p.poll() is None:
            if cancel is not None and cancel.is_set():
                p.kill()
                p.wait()
                raise Dibatalkan()
            time.sleep(0.2)
        if p.returncode != 0:
            err.seek(0)
            raise RuntimeError(err.read()[-300:])


def info_codec(path):
    r = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "stream=codec_type,codec_name",
         "-of", "json", path], capture_output=True, text=True)
    d = {"video": None, "audio": None}
    for s in json.loads(r.stdout).get("streams", []):
        d[s["codec_type"]] = s["codec_name"]
    return d
