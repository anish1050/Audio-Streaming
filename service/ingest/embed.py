"""Audio embeddings for recommendations.

Usage (WSL2 or Docker, from service/):
    python -m venv ingest/.venv && . ingest/.venv/bin/activate
    pip install -r ingest/requirements.txt
    CLAP_CKPT=/path/music_audioset_epoch_15_esc_90.14.pt \
    MONGODB_URI=... MONGODB_DBNAME=hires python ingest/embed.py --work ../drops/.work

Reads <work>/<track_id>/lossless.flac, written by ingest-album, for every track
without an embedding. Stores a unit-length 512-d LAION-CLAP vector and, when
the track has no genres yet, the two closest zero-shot genre labels.
Confirm the CLAP checkpoint licence allows commercial use before running.
"""

import argparse
import os

import laion_clap
import numpy as np
from pymongo import MongoClient

GENRES = ["lo-fi", "pop", "r&b", "hip-hop", "rock", "indie", "electronic", "jazz", "classical", "ambient", "folk", "metal", "bollywood", "devotional"]


def unit(v: np.ndarray) -> np.ndarray:
    return v / np.linalg.norm(v, axis=-1, keepdims=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--work", required=True, help="the drops/.work folder written by ingest-album")
    args = parser.parse_args()

    db = MongoClient(os.environ["MONGODB_URI"])[os.environ["MONGODB_DBNAME"]]
    model = laion_clap.CLAP_Module(enable_fusion=False, amodel="HTSAT-base")
    model.load_ckpt(os.environ["CLAP_CKPT"])
    genre_vectors = unit(model.get_text_embedding([f"{g} music" for g in GENRES]))

    pending = list(db.catalog_tracks.find({"embedding": {"$size": 0}}, {"_id": 1, "genres": 1}))
    for track in pending:
        path = os.path.join(args.work, track["_id"], "lossless.flac")
        if not os.path.exists(path):
            print(f"skip {track['_id']}: {path} missing")
            continue
        audio = unit(model.get_audio_embedding_from_filelist(x=[path])[0])
        update = {"embedding": audio.astype(float).tolist()}
        if not track.get("genres"):
            update["genres"] = [GENRES[i] for i in np.argsort(-(genre_vectors @ audio))[:2]]
        db.catalog_tracks.update_one({"_id": track["_id"]}, {"$set": update})
        print(f"embedded {track['_id']}")


if __name__ == "__main__":
    main()
