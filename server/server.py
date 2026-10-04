import os, sys, uuid, shutil, threading, secrets, time
sys.path.append("/content/drive/MyDrive/ai-toolkit")

from fastapi import FastAPI, UploadFile, File, Form, HTTPException, Depends
from fastapi.responses import FileResponse
from fastapi.security import APIKeyHeader

from core.config import WORK
from core.ffmpeg_utils import jenis_file, set_cancel_event, Dibatalkan
from tools.converter import convert_pintar
from tools.subtitle import buat_subtitle, FORMAT_SUBTITLE

API_KEY = os.environ.get("API_KEY")
if not API_KEY:
    raise RuntimeError("API_KEY belum di-set")

key_header = APIKeyHeader(name="X-API-Key")


def cek_key(key: str = Depends(key_header)):
    if not secrets.compare_digest(key, API_KEY):
        raise HTTPException(status_code=401, detail="API key salah")


app = FastAPI(title="ClipForge Server")
JOBS = {}
JOBS_DIR = os.path.join(WORK, "jobs")
os.makedirs(JOBS_DIR, exist_ok=True)
ANTRIAN = threading.Semaphore(1)   # satu pekerjaan berat dalam satu waktu


def ekstensi(nama):
    return os.path.splitext(nama)[1].lstrip(".").lower()


def kerjakan(job_id):
    job = JOBS.get(job_id)
    if job is None:
        return
    with ANTRIAN:
        if job["cancel"].is_set():
            job["status"] = "cancelled"
            return
        job["status"] = "running"
        set_cancel_event(job["cancel"])
        try:
            if job["kind"] == "subtitle":
                out = buat_subtitle(job["input"], job["out_dir"], job["language"],
                                    job["fmt"], job["cancel"], job.get("maxchars", 0))
            else:
                out = convert_pintar(job["input"], job["fmt"], job["out_dir"])
            job["output"] = out
            job["status"] = "done"
        except Dibatalkan:
            job["status"] = "cancelled"
        except Exception as e:
            job["error"] = str(e)[:500]
            job["status"] = "error"
        finally:
            set_cancel_event(None)


def simpan_dan_jalankan(file, kind, fmt, **extra):
    nama = os.path.basename(file.filename or "file")
    job_id = uuid.uuid4().hex[:12]
    folder = os.path.join(JOBS_DIR, job_id)
    in_dir = os.path.join(folder, "in")
    out_dir = os.path.join(folder, "out")
    os.makedirs(in_dir)
    os.makedirs(out_dir)

    in_path = os.path.join(in_dir, nama)
    with open(in_path, "wb") as f:
        shutil.copyfileobj(file.file, f)

    JOBS[job_id] = {"kind": kind, "status": "queued", "input": in_path, "fmt": fmt,
                    "out_dir": out_dir, "folder": folder,
                    "output": None, "error": None,
                    "cancel": threading.Event(), **extra}
    threading.Thread(target=kerjakan, args=(job_id,), daemon=True).start()
    return job_id


@app.get("/health")
def health():
    return {"ok": True}


@app.post("/jobs", dependencies=[Depends(cek_key)])
def buat_job(file: UploadFile = File(...), fmt: str = Form(...)):
    fmt = fmt.lower().lstrip(".")
    nama = os.path.basename(file.filename or "file")
    if jenis_file(ekstensi(nama)) is None:
        raise HTTPException(400, f"Format file tidak didukung: .{ekstensi(nama)}")
    if jenis_file(fmt) is None:
        raise HTTPException(400, f"Format tujuan tidak didukung: .{fmt}")
    return {"job_id": simpan_dan_jalankan(file, "convert", fmt)}


@app.post("/subtitle", dependencies=[Depends(cek_key)])
def buat_subtitle_job(file: UploadFile = File(...), fmt: str = Form("srt"),
                      language: str = Form("id"), maxchars: int = Form(0)):
    fmt = fmt.lower().lstrip(".")
    nama = os.path.basename(file.filename or "file")
    if jenis_file(ekstensi(nama)) not in ("video", "audio"):
        raise HTTPException(400, "Subtitle hanya untuk file video atau audio")
    if fmt not in FORMAT_SUBTITLE:
        raise HTTPException(400, f"Format subtitle tidak didukung: {fmt}")
    maxchars = max(0, min(maxchars, 80))
    return {"job_id": simpan_dan_jalankan(file, "subtitle", fmt,
                                          language=language.lower(), maxchars=maxchars)}


def ambil(job_id):
    job = JOBS.get(job_id)
    if not job:
        raise HTTPException(404, "Job tidak ditemukan")
    return job


@app.get("/jobs/{job_id}", dependencies=[Depends(cek_key)])
def status_job(job_id: str):
    job = ambil(job_id)
    return {"status": job["status"], "error": job["error"],
            "filename": os.path.basename(job["output"]) if job["output"] else None}


@app.get("/jobs/{job_id}/download", dependencies=[Depends(cek_key)])
def unduh(job_id: str):
    job = ambil(job_id)
    if job["status"] != "done":
        raise HTTPException(409, f"Belum selesai (status: {job['status']})")
    return FileResponse(job["output"], filename=os.path.basename(job["output"]))


@app.delete("/jobs/{job_id}", dependencies=[Depends(cek_key)])
def hapus(job_id: str):
    job = ambil(job_id)
    job["cancel"].set()
    del JOBS[job_id]

    def bersihkan():
        for _ in range(50):
            if job["status"] != "running":
                break
            time.sleep(0.2)
        shutil.rmtree(job["folder"], ignore_errors=True)

    threading.Thread(target=bersihkan, daemon=True).start()
    return {"deleted": job_id}
