import os, threading
from core.ffmpeg_utils import run_ffmpeg, Dibatalkan, jenis_file

MODEL_SIZE = "small"          # ganti ke "medium" atau "large-v3" untuk akurasi lebih tinggi
FORMAT_SUBTITLE = ("srt", "vtt", "txt")

_model = None
_lock = threading.Lock()


def _get_model():
    global _model
    with _lock:
        if _model is None:
            from faster_whisper import WhisperModel
            try:
                _model = WhisperModel(MODEL_SIZE, device="cuda", compute_type="float16")
            except Exception:
                _model = WhisperModel(MODEL_SIZE, device="cpu", compute_type="int8")
        return _model


def _ts(detik, pemisah):
    ms = int(round(detik * 1000))
    h, ms = divmod(ms, 3_600_000)
    m, ms = divmod(ms, 60_000)
    s, ms = divmod(ms, 1000)
    return f"{h:02}:{m:02}:{s:02}{pemisah}{ms:03}"


def _kelompokkan(kata, max_chars, jeda=0.7, max_durasi=4.0):
    """Gabungkan kata jadi potongan pendek: (mulai, selesai, teks)."""
    hasil, buf = [], []

    def tutup():
        if buf:
            teks = "".join(w.word for w in buf).strip()
            if teks:
                hasil.append((buf[0].start, buf[-1].end, teks))
            buf.clear()

    for w in kata:
        if buf:
            panjang = len("".join(x.word for x in buf) + w.word)
            if (panjang > max_chars
                    or (w.start - buf[-1].end) > jeda
                    or (w.end - buf[0].start) > max_durasi):
                tutup()
        buf.append(w)
        if w.word.strip().endswith((".", "?", "!")):
            tutup()
    tutup()
    return hasil


def buat_subtitle(input_path, out_dir, language="id", fmt="srt", cancel=None, max_chars=0):
    ext = os.path.splitext(input_path)[1].lstrip(".").lower()
    if jenis_file(ext) not in ("video", "audio"):
        raise ValueError("Subtitle hanya untuk file video atau audio")
    fmt = fmt.lower()
    if fmt not in FORMAT_SUBTITLE:
        raise ValueError(f"Format subtitle tidak didukung: {fmt}")

    nama = os.path.splitext(os.path.basename(input_path))[0]
    wav = os.path.join(os.path.dirname(out_dir), "audio.wav")
    run_ffmpeg(["ffmpeg", "-y", "-i", input_path, "-vn", "-ac", "1", "-ar", "16000", wav])

    model = _get_model()
    lang = None if language in (None, "", "auto") else language
    pendek = max_chars and max_chars > 0
    segmen, _ = model.transcribe(wav, language=lang, vad_filter=True, beam_size=5,
                                 word_timestamps=bool(pendek))

    out = os.path.join(out_dir, f"{nama}.{fmt}")
    n = 0
    with open(out, "w", encoding="utf-8") as f:
        if fmt == "vtt":
            f.write("WEBVTT\n\n")
        for seg in segmen:
            if cancel is not None and cancel.is_set():
                raise Dibatalkan()
            if pendek and seg.words:
                potongan = _kelompokkan(seg.words, max_chars)
            else:
                potongan = [(seg.start, seg.end, seg.text.strip())]
            for mulai, selesai, teks in potongan:
                if not teks:
                    continue
                n += 1
                if fmt == "txt":
                    f.write(teks + "\n")
                elif fmt == "srt":
                    f.write(f"{n}\n{_ts(mulai, ',')} --> {_ts(selesai, ',')}\n{teks}\n\n")
                else:
                    f.write(f"{_ts(mulai, '.')} --> {_ts(selesai, '.')}\n{teks}\n\n")
    if n == 0:
        raise ValueError("Tidak ada ucapan yang terdeteksi")
    return out
