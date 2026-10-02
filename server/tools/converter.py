import os
from core.config import VIDEO_FMT, AUDIO_FMT, IMAGE_FMT, CODEC, REMUX_OK
from core.ffmpeg_utils import jenis_file, info_codec, run_ffmpeg

ALL_FORMATS = sorted(set(VIDEO_FMT + AUDIO_FMT + IMAGE_FMT))


def convert_pintar(input_path, output_ext, output_dir):
    output_ext = output_ext.lower().lstrip(".")
    in_ext = os.path.splitext(input_path)[1].lstrip(".").lower()
    nama = os.path.splitext(os.path.basename(input_path))[0]
    out = os.path.join(output_dir, f"{nama}.{output_ext}")
    if os.path.abspath(out) == os.path.abspath(input_path):
        out = os.path.join(output_dir, f"{nama}_convert.{output_ext}")

    j_in, j_out = jenis_file(in_ext), jenis_file(output_ext)
    if j_in is None or j_out is None:
        raise ValueError(f"Format tidak didukung: .{in_ext} -> .{output_ext}")

    # Aturan konversi yang masuk akal
    boleh = (j_in == j_out) or (j_in == "video" and j_out == "audio")
    if not boleh:
        raise ValueError(f"Tidak bisa convert {j_in} ke {j_out}")

    # 1) Remux: video H.264+AAC ke container yang cocok (instan)
    if j_in == "video" and j_out == "video" and output_ext in REMUX_OK:
        c = info_codec(input_path)
        if c["video"] == "h264" and c["audio"] in ("aac", None):
            run_ffmpeg(["ffmpeg", "-y", "-i", input_path, "-c", "copy", out])
            return out

    # 2) Re-encode
    cmd = ["ffmpeg", "-y", "-i", input_path]
    if j_out == "audio":
        cmd += ["-vn"]
    if j_out == "image" and j_in == "video":
        cmd += ["-frames:v", "1"]
    cmd += CODEC.get(output_ext, [])
    cmd += [out]
    run_ffmpeg(cmd)
    return out
