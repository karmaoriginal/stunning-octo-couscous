#!/usr/bin/env python3
"""
Kromiv CC0 1.0 Original Artwork & Catalog Generator
Generates original procedural static WebP wallpapers, seamless-looping MP4 live wallpapers,
short preview clips, WebP thumbnails, and docs/catalog.json.

All generated assets are released under CC0 1.0 Universal (Public Domain).
"""

import argparse
import hashlib
import json
import math
import os
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

import imageio_ffmpeg
import numpy as np
from PIL import Image, ImageDraw, ImageFilter


FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()

GITHUB_OWNER = "karmaoriginal"
GITHUB_REPO = "stunning-octo-couscous"
RELEASE_TAG = "catalog-v1"
PAGES_BASE = f"https://{GITHUB_OWNER}.github.io/{GITHUB_REPO}"
RELEASE_BASE = f"https://github.com/{GITHUB_OWNER}/{GITHUB_REPO}/releases/download/{RELEASE_TAG}"


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


def make_grid(w: int, h: int):
    y = np.linspace(-1.0, 1.0, h, dtype=np.float32)[:, None]
    x = np.linspace(-1.0, 1.0, w, dtype=np.float32)[None, :]
    aspect = h / float(w)
    return x, y * aspect


def clip_u8(arr: np.ndarray) -> np.ndarray:
    return np.clip(arr * 255.0, 0.0, 255.0).astype(np.uint8)


def add_fine_grain(rgb: np.ndarray, strength: float = 0.015, seed: int = 42) -> np.ndarray:
    rng = np.random.default_rng(seed)
    noise = rng.normal(0.0, strength, size=(rgb.shape[0], rgb.shape[1], 1)).astype(np.float32)
    return np.clip(rgb + noise, 0.0, 1.0)


def render_static_amoled_gold(w: int, h: int) -> Image.Image:
    x, y = make_grid(w, h)
    r = np.sqrt(x * x + y * y)
    wave1 = np.sin(x * 3.8 + np.cos(y * 2.4) * 1.9)
    wave2 = np.cos(y * 3.1 - np.sin(x * 2.7) * 1.7)
    ribbon = np.exp(-((wave1 + wave2) ** 2) * 18.0)
    halo = np.exp(-((r - 0.65) ** 2) * 6.5) * 0.35
    glow = np.clip(ribbon * 1.15 + halo, 0.0, 1.0)
    rgb = np.zeros((h, w, 3), dtype=np.float32)
    rgb[..., 0] = glow * 1.00 + (ribbon ** 2) * 0.25
    rgb[..., 1] = glow * 0.72 + (ribbon ** 3) * 0.22
    rgb[..., 2] = glow * 0.24 + (halo ** 2) * 0.35
    # Keep true #000000 AMOLED background where glow is minimal
    mask = (glow > 0.015).astype(np.float32)[..., None]
    rgb = rgb * mask
    return Image.fromarray(clip_u8(rgb), "RGB")


def render_static_abstract_prism(w: int, h: int) -> Image.Image:
    x, y = make_grid(w, h)
    a = np.sin(x * 2.5 + y * 1.7) * np.cos(y * 2.2 - x * 1.1)
    b = np.sin((x * x + y * y) * 2.4 - x * 1.8)
    c = np.cos(x * 4.2 - y * 3.1)
    rgb = np.zeros((h, w, 3), dtype=np.float32)
    rgb[..., 0] = 0.20 + 0.45 * (0.5 + 0.5 * a) + 0.28 * np.maximum(0.0, b)
    rgb[..., 1] = 0.14 + 0.40 * (0.5 + 0.5 * c) + 0.32 * (0.5 - 0.5 * a)
    rgb[..., 2] = 0.32 + 0.55 * (0.5 + 0.5 * np.sin(a * 2.1 + c))
    rgb = add_fine_grain(rgb, 0.012, 101)
    return Image.fromarray(clip_u8(rgb), "RGB")


def render_static_space_carina(w: int, h: int) -> Image.Image:
    x, y = make_grid(w, h)
    neb1 = np.sin(x * 2.3 + np.sin(y * 2.8)) * 0.5 + 0.5
    neb2 = np.cos(y * 3.2 - np.cos(x * 2.1) * 1.6) * 0.5 + 0.5
    core = np.exp(-((x + 0.15) ** 2 * 1.8 + (y - 0.1) ** 2 * 0.9))
    dust = (neb1 * neb2) ** 1.4
    rgb = np.zeros((h, w, 3), dtype=np.float32)
    rgb[..., 0] = 0.03 + 0.52 * dust * neb1 + 0.45 * core
    rgb[..., 1] = 0.04 + 0.28 * dust * neb2 + 0.25 * core
    rgb[..., 2] = 0.09 + 0.65 * (neb2 ** 1.5) * 0.55 + 0.55 * core
    rng = np.random.default_rng(7)
    stars = rng.random((h, w))
    star_mask = (stars > 0.9965).astype(np.float32) * (0.6 + 0.4 * rng.random((h, w), dtype=np.float32))
    rgb += star_mask[..., None]
    return Image.fromarray(clip_u8(rgb), "RGB")


def render_static_nature_canopy(w: int, h: int) -> Image.Image:
    x, y = make_grid(w, h)
    t = (y + 1.8) / 3.6
    # Sunbeams diagonal
    beam_coord = x * 0.85 - y * 0.42
    beams = (0.5 + 0.5 * np.sin(beam_coord * 9.0)) ** 3 * np.exp(-((y + 0.8) ** 2) * 0.6)
    rgb = np.zeros((h, w, 3), dtype=np.float32)
    rgb[..., 0] = 0.04 + 0.18 * (1.0 - t) + 0.48 * beams
    rgb[..., 1] = 0.14 + 0.34 * (1.0 - t * 0.7) + 0.55 * beams
    rgb[..., 2] = 0.10 + 0.20 * (1.0 - t) + 0.28 * beams
    img = Image.fromarray(clip_u8(rgb), "RGB")
    draw = ImageDraw.Draw(img, "RGBA")
    for layer, (color, base_y, amp) in enumerate([
        ((10, 42, 34, 210), 0.58, 0.07),
        ((6, 28, 22, 235), 0.72, 0.06),
        ((3, 15, 12, 255), 0.84, 0.05),
    ]):
        pts = [(0, h)]
        for i in range(0, 41):
            px = int(w * i / 40)
            py = int(h * (base_y + amp * math.sin(i * 0.45 + layer * 1.9) + 0.02 * math.cos(i * 1.1)))
            pts.append((px, py))
        pts.append((w, h))
        draw.polygon(pts, fill=color)
    return img


def render_static_water_caustics(w: int, h: int) -> Image.Image:
    x, y = make_grid(w, h)
    c1 = np.abs(np.sin(x * 5.2 + np.cos(y * 4.1) * 1.5))
    c2 = np.abs(np.cos(y * 4.7 - np.sin(x * 3.8) * 1.6))
    caustic = np.exp(-((c1 * c2) ** 0.7) * 5.5)
    depth = np.clip((y + 1.8) / 3.6, 0.0, 1.0)
    rgb = np.zeros((h, w, 3), dtype=np.float32)
    rgb[..., 0] = 0.02 + 0.22 * caustic * (1.0 - 0.5 * depth)
    rgb[..., 1] = 0.18 + 0.35 * (1.0 - depth) + 0.62 * caustic
    rgb[..., 2] = 0.32 + 0.42 * (1.0 - 0.4 * depth) + 0.58 * caustic
    return Image.fromarray(clip_u8(rgb), "RGB")


def render_static_colors_bloom(w: int, h: int) -> Image.Image:
    x, y = make_grid(w, h)
    b1 = np.exp(-((x + 0.45) ** 2 + (y + 0.55) ** 2) * 1.6)
    b2 = np.exp(-((x - 0.45) ** 2 + (y - 0.15) ** 2) * 1.5)
    b3 = np.exp(-((x + 0.15) ** 2 + (y - 0.85) ** 2) * 1.4)
    rgb = np.zeros((h, w, 3), dtype=np.float32)
    rgb[..., 0] = 0.09 + 0.88 * b1 + 0.42 * b3
    rgb[..., 1] = 0.07 + 0.32 * b1 + 0.75 * b2 + 0.18 * b3
    rgb[..., 2] = 0.14 + 0.78 * b2 + 0.85 * b3
    rgb = add_fine_grain(rgb, 0.015, 33)
    return Image.fromarray(clip_u8(rgb), "RGB")


def render_static_minimal_monolith(w: int, h: int) -> Image.Image:
    x, y = make_grid(w, h)
    t = np.clip((y + 1.8) / 3.6, 0.0, 1.0)
    rgb = np.zeros((h, w, 3), dtype=np.float32)
    rgb[..., 0] = 0.94 - 0.16 * t
    rgb[..., 1] = 0.90 - 0.20 * t
    rgb[..., 2] = 0.84 - 0.14 * t
    rgb = add_fine_grain(rgb, 0.012, 5)
    img = Image.fromarray(clip_u8(rgb), "RGB")
    draw = ImageDraw.Draw(img)
    cx, cy = w // 2, int(h * 0.42)
    rad = int(w * 0.28)
    draw.ellipse((cx - rad, cy - rad, cx + rad, cy + rad), fill=(217, 94, 64))
    # Clean architectural monolith
    mw = int(w * 0.36)
    mh = int(h * 0.38)
    draw.rectangle((cx - mw // 2, int(h * 0.48), cx + mw // 2, int(h * 0.48) + mh), fill=(24, 28, 38))
    return img


def render_static_landscapes_alpine(w: int, h: int) -> Image.Image:
    x, y = make_grid(w, h)
    t = np.clip((y + 1.8) / 3.6, 0.0, 1.0)
    rgb = np.zeros((h, w, 3), dtype=np.float32)
    rgb[..., 0] = 0.12 + 0.78 * (t ** 1.3)
    rgb[..., 1] = 0.10 + 0.42 * (t ** 1.5)
    rgb[..., 2] = 0.28 + 0.36 * (1.0 - t)
    img = Image.fromarray(clip_u8(rgb), "RGB")
    draw = ImageDraw.Draw(img)
    # Crescent moon
    mx, my, mr = int(w * 0.72), int(h * 0.22), int(w * 0.07)
    draw.ellipse((mx - mr, my - mr, mx + mr, my + mr), fill=(252, 240, 215))
    draw.ellipse((mx - mr + int(mr * 0.45), my - mr - int(mr * 0.2), mx + mr + int(mr * 0.45), my + mr - int(mr * 0.2)), fill=(38, 32, 74))
    ridges = [
        ((76, 56, 98), 0.52, 0.08, 0.2),
        ((46, 36, 72), 0.62, 0.07, 1.7),
        ((26, 22, 48), 0.73, 0.06, 3.1),
        ((12, 12, 24), 0.84, 0.05, 4.4),
    ]
    for color, base_y, amp, phase in ridges:
        pts = [(0, h)]
        for i in range(0, 61):
            px = int(w * i / 60)
            py = int(h * (base_y + amp * math.sin(i * 0.28 + phase) + 0.025 * math.cos(i * 0.8 + phase)))
            pts.append((px, py))
        pts.append((w, h))
        draw.polygon(pts, fill=color)
    return img


def render_static_anime_torii(w: int, h: int) -> Image.Image:
    x, y = make_grid(w, h)
    t = np.clip((y + 1.8) / 3.6, 0.0, 1.0)
    rgb = np.zeros((h, w, 3), dtype=np.float32)
    rgb[..., 0] = 0.14 + 0.75 * (t ** 1.1)
    rgb[..., 1] = 0.08 + 0.30 * (t ** 1.6)
    rgb[..., 2] = 0.34 + 0.22 * np.sin(t * math.pi)
    img = Image.fromarray(clip_u8(rgb), "RGB")
    draw = ImageDraw.Draw(img, "RGBA")
    # Stylized glowing moon
    cx, cy, r = w // 2, int(h * 0.46), int(w * 0.27)
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(255, 224, 200, 240))
    # Stylized Torii silhouette
    ink = (18, 12, 28, 255)
    pillar_offset = int(w * 0.15)
    pw = int(w * 0.045)
    top_y = int(h * 0.42)
    bot_y = int(h * 0.78)
    draw.rectangle((cx - pillar_offset - pw // 2, top_y, cx - pillar_offset + pw // 2, bot_y), fill=ink)
    draw.rectangle((cx + pillar_offset - pw // 2, top_y, cx + pillar_offset + pw // 2, bot_y), fill=ink)
    # Kasagi curved top beam
    draw.rounded_rectangle((cx - int(w * 0.29), top_y - int(h * 0.022), cx + int(w * 0.29), top_y + int(h * 0.008)), radius=14, fill=ink)
    draw.rectangle((cx - int(w * 0.22), top_y + int(h * 0.045), cx + int(w * 0.22), top_y + int(h * 0.068)), fill=ink)
    draw.rectangle((0, int(h * 0.76), w, h), fill=(12, 8, 22, 255))
    # Sakura petals
    rng = np.random.default_rng(99)
    for _ in range(42):
        px = int(rng.uniform(0.05, 0.95) * w)
        py = int(rng.uniform(0.12, 0.75) * h)
        pr = int(rng.uniform(5, 13))
        draw.ellipse((px - pr, py - pr // 2, px + pr, py + pr // 2), fill=(255, 182, 206, 210))
    return img


def render_video_frame_amoled_pulse(w: int, h: int, phase: float) -> np.ndarray:
    x, y = make_grid(w, h)
    ang = phase * 2.0 * math.pi
    wave1 = np.sin(x * 3.2 + math.sin(ang) * 1.4 + np.cos(y * 2.5 + ang) * 1.2)
    wave2 = np.cos(y * 2.9 - math.cos(ang) * 1.3 + np.sin(x * 2.1 - ang))
    ribbon = np.exp(-((wave1 + wave2) ** 2) * 16.0)
    rgb = np.zeros((h, w, 3), dtype=np.float32)
    rgb[..., 0] = ribbon * (0.85 + 0.15 * math.sin(ang))
    rgb[..., 1] = ribbon * (0.12 + 0.10 * math.cos(ang))
    rgb[..., 2] = ribbon * (0.45 + 0.35 * math.sin(ang + 1.2))
    mask = (ribbon > 0.015).astype(np.float32)[..., None]
    return clip_u8(rgb * mask)


def render_video_frame_kinetic_silk(w: int, h: int, phase: float) -> np.ndarray:
    x, y = make_grid(w, h)
    ang = phase * 2.0 * math.pi
    f1 = np.sin(x * 2.6 + np.cos(y * 2.2 + ang) * 1.8)
    f2 = np.cos(y * 3.1 + np.sin(x * 2.4 - ang) * 1.6)
    blend = 0.5 + 0.5 * np.sin(f1 * 1.8 + f2 * 1.5 + ang)
    rgb = np.zeros((h, w, 3), dtype=np.float32)
    rgb[..., 0] = 0.12 + 0.55 * (blend ** 1.4)
    rgb[..., 1] = 0.16 + 0.62 * (0.5 + 0.5 * f2)
    rgb[..., 2] = 0.35 + 0.60 * (0.5 + 0.5 * f1)
    return clip_u8(rgb)


def render_video_frame_space_orbit(w: int, h: int, phase: float) -> np.ndarray:
    x, y = make_grid(w, h)
    ang = phase * 2.0 * math.pi
    cx = 0.0 + 0.04 * math.sin(ang)
    cy = 0.65 + 0.03 * math.cos(ang)
    dist = np.sqrt((x - cx) ** 2 + (y - cy) ** 2)
    limb = np.exp(-((dist - 0.92) ** 2) * 55.0)
    atmo = np.exp(-np.maximum(0.0, dist - 0.92) * 5.5) * (dist >= 0.90)
    planet = (dist < 0.91).astype(np.float32)
    rgb = np.zeros((h, w, 3), dtype=np.float32)
    rgb[..., 0] = 0.02 + 0.25 * limb + 0.12 * atmo
    rgb[..., 1] = 0.04 + 0.65 * limb + 0.42 * atmo
    rgb[..., 2] = 0.08 + 0.95 * limb + 0.85 * atmo
    rgb = rgb * (1.0 - 0.92 * planet[..., None])
    # Twinkling deterministic stars
    rng = np.random.default_rng(12)
    star_y = rng.integers(0, int(h * 0.65), size=80)
    star_x = rng.integers(0, w, size=80)
    twinkle = 0.55 + 0.45 * np.sin(ang + np.arange(80) * 0.7)
    for idx in range(80):
        sy, sx = star_y[idx], star_x[idx]
        rgb[sy:sy + 2, sx:sx + 2] = np.maximum(rgb[sy:sy + 2, sx:sx + 2], twinkle[idx])
    return clip_u8(rgb)


def render_video_frame_nature_fireflies(w: int, h: int, phase: float) -> np.ndarray:
    x, y = make_grid(w, h)
    ang = phase * 2.0 * math.pi
    t = np.clip((y + 1.8) / 3.6, 0.0, 1.0)
    rgb = np.zeros((h, w, 3), dtype=np.float32)
    rgb[..., 0] = 0.02 + 0.04 * (1.0 - t)
    rgb[..., 1] = 0.06 + 0.16 * (1.0 - t)
    rgb[..., 2] = 0.05 + 0.10 * (1.0 - t)
    rng = np.random.default_rng(2026)
    for i in range(18):
        fx = rng.uniform(-0.85, 0.85) + 0.12 * math.sin(ang + i * 1.1)
        fy = rng.uniform(-1.3, 1.3) + 0.15 * math.cos(ang + i * 0.9)
        pulse = 0.4 + 0.6 * (0.5 + 0.5 * math.sin(ang * 2.0 + i * 1.7))
        d = np.exp(-((x - fx) ** 2 + (y - fy) ** 2) * 140.0) * pulse
        rgb[..., 0] += d * 0.95
        rgb[..., 1] += d * 0.88
        rgb[..., 2] += d * 0.25
    return clip_u8(rgb)


def render_video_frame_water_biolume(w: int, h: int, phase: float) -> np.ndarray:
    x, y = make_grid(w, h)
    ang = phase * 2.0 * math.pi
    w1 = np.sin(x * 4.5 + ang + np.cos(y * 3.2 - ang) * 1.4)
    w2 = np.cos(y * 4.1 - ang + np.sin(x * 3.6 + ang) * 1.3)
    crest = np.exp(-((w1 + w2) ** 2) * 9.0)
    rgb = np.zeros((h, w, 3), dtype=np.float32)
    rgb[..., 0] = 0.01 + 0.14 * crest
    rgb[..., 1] = 0.10 + 0.72 * crest
    rgb[..., 2] = 0.22 + 0.78 * crest
    return clip_u8(rgb)


def render_video_frame_colors_spectral(w: int, h: int, phase: float) -> np.ndarray:
    x, y = make_grid(w, h)
    ang = phase * 2.0 * math.pi
    val = np.sin(x * 2.4 + y * 1.9 + ang) + np.cos(y * 2.8 - x * 1.4 - ang)
    rgb = np.zeros((h, w, 3), dtype=np.float32)
    rgb[..., 0] = 0.5 + 0.48 * np.sin(val * 1.4 + ang)
    rgb[..., 1] = 0.5 + 0.48 * np.sin(val * 1.4 + ang + 2.094)
    rgb[..., 2] = 0.5 + 0.48 * np.sin(val * 1.4 + ang + 4.188)
    return clip_u8(rgb)


def render_video_frame_minimal_eclipse(w: int, h: int, phase: float) -> np.ndarray:
    x, y = make_grid(w, h)
    ang = phase * 2.0 * math.pi
    r = np.sqrt(x * x + y * y)
    radius = 0.42 + 0.025 * math.sin(ang)
    ring = np.exp(-((r - radius) ** 2) * 320.0)
    aura = np.exp(-np.maximum(0.0, r - radius) * 6.5) * 0.35
    rgb = np.zeros((h, w, 3), dtype=np.float32)
    rgb[..., 0] = 0.06 + 0.92 * ring + 0.45 * aura
    rgb[..., 1] = 0.06 + 0.65 * ring + 0.22 * aura
    rgb[..., 2] = 0.08 + 0.45 * ring + 0.15 * aura
    return clip_u8(rgb)


def render_video_frame_landscapes_aurora(w: int, h: int, phase: float) -> np.ndarray:
    x, y = make_grid(w, h)
    ang = phase * 2.0 * math.pi
    curtain = np.exp(-((y + 0.45 + 0.22 * np.sin(x * 3.0 + ang)) ** 2) * 6.0)
    shimmer = 0.55 + 0.45 * np.sin(x * 8.0 + ang * 2.0)
    sky = np.clip(1.0 - (y + 1.8) / 3.6, 0.0, 1.0)
    rgb = np.zeros((h, w, 3), dtype=np.float32)
    rgb[..., 0] = 0.02 + 0.22 * curtain * (1.0 - shimmer)
    rgb[..., 1] = 0.04 + 0.85 * curtain * shimmer
    rgb[..., 2] = 0.10 + 0.52 * curtain + 0.08 * sky
    # Mountain silhouette at bottom
    ridge = 0.55 + 0.12 * np.sin(x * 4.5 + 1.1) + 0.06 * np.cos(x * 9.0)
    mask = (y < ridge).astype(np.float32)[..., None]
    rgb = rgb * mask + np.array([0.02, 0.02, 0.05], dtype=np.float32) * (1.0 - mask)
    return clip_u8(rgb)


def render_video_frame_anime_petals(w: int, h: int, phase: float) -> np.ndarray:
    x, y = make_grid(w, h)
    t = np.clip((y + 1.8) / 3.6, 0.0, 1.0)
    rgb = np.zeros((h, w, 3), dtype=np.float32)
    rgb[..., 0] = 0.11 + 0.35 * (t ** 1.3)
    rgb[..., 1] = 0.07 + 0.14 * (t ** 1.5)
    rgb[..., 2] = 0.28 + 0.24 * np.sin(t * math.pi)
    # Moon glow
    moon_d = np.sqrt((x - 0.15) ** 2 + (y + 0.55) ** 2)
    moon = (moon_d < 0.24).astype(np.float32)
    rgb[..., 0] = rgb[..., 0] * (1.0 - moon) + 1.0 * moon
    rgb[..., 1] = rgb[..., 1] * (1.0 - moon) + 0.90 * moon
    rgb[..., 2] = rgb[..., 2] * (1.0 - moon) + 0.82 * moon
    # Looping drifting sakura petals
    rng = np.random.default_rng(77)
    for i in range(22):
        base_x = rng.uniform(-1.0, 1.0)
        base_y = rng.uniform(-1.8, 1.8)
        px = ((base_x + phase * 1.0 * (1.0 if i % 2 == 0 else 0.5) + 1.0) % 2.0) - 1.0
        py = ((base_y + phase * 3.6 + 1.8) % 3.6) - 1.8
        d = np.exp(-((x - px) ** 2 * 380.0 + (y - py) ** 2 * 700.0))
        rgb[..., 0] = np.clip(rgb[..., 0] + d * 0.98, 0.0, 1.0)
        rgb[..., 1] = np.clip(rgb[..., 1] + d * 0.62, 0.0, 1.0)
        rgb[..., 2] = np.clip(rgb[..., 2] + d * 0.78, 0.0, 1.0)
    return clip_u8(rgb)


def encode_loop_mp4(
    frame_fn: Callable[[int, int, float], np.ndarray],
    out_full: Path,
    out_preview: Path,
    out_thumb: Path,
    w: int = 1080,
    h: int = 1920,
    fps: int = 30,
    duration_sec: int = 4,
):
    # Render at 540x960 then crisp lanczos upscale or native 540x960->1080x1920 in ffmpeg for fast clean generation
    rw, rh = 540, 960
    total_frames = fps * duration_sec
    raw_frames_dir = out_full.parent / f"_frames_{out_full.stem}"
    raw_frames_dir.mkdir(parents=True, exist_ok=True)
    for idx in range(total_frames):
        phase = idx / float(total_frames)
        arr = frame_fn(rw, rh, phase)
        img = Image.fromarray(arr, "RGB")
        if idx == 0:
            thumb = img.resize((360, 640), Image.Resampling.LANCZOS)
            thumb.save(out_thumb, "WEBP", quality=82, method=6)
        img.save(raw_frames_dir / f"f_{idx:04d}.png", compress_level=1)

    # Full 1080x1920 seamless MP4
    subprocess.run(
        [
            FFMPEG, "-y",
            "-framerate", str(fps),
            "-i", str(raw_frames_dir / "f_%04d.png"),
            "-vf", f"scale={w}:{h}:flags=lanczos,format=yuv420p",
            "-c:v", "libx264", "-preset", "medium", "-crf", "20",
            "-movflags", "+faststart", "-an",
            str(out_full),
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )

    # Lightweight 360x640 short preview MP4 (~2.5s)
    subprocess.run(
        [
            FFMPEG, "-y",
            "-framerate", str(fps),
            "-i", str(raw_frames_dir / "f_%04d.png"),
            "-t", "2.5",
            "-vf", "scale=360:640:flags=lanczos,format=yuv420p",
            "-c:v", "libx264", "-preset", "fast", "-crf", "26",
            "-movflags", "+faststart", "-an",
            str(out_preview),
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )

    for p in raw_frames_dir.iterdir():
        p.unlink()
    raw_frames_dir.rmdir()


@dataclass
class StaticSpec:
    id: str
    title: str
    category: str
    popularity: int
    created_ms: int
    tags: list[str]
    render: Callable[[int, int], Image.Image]


@dataclass
class VideoSpec:
    id: str
    title: str
    category: str
    popularity: int
    created_ms: int
    tags: list[str]
    render_frame: Callable[[int, int, float], np.ndarray]


STATIC_SPECS = [
    StaticSpec("amoled-obsidian-gold", "Obsidian Gold", "amoled", 98, 1763500000000, ["amoled", "gold", "negro", "minimal"], render_static_amoled_gold),
    StaticSpec("abstract-prism-glass", "Prism Glass", "abstract", 92, 1763400000000, ["prisma", "vidrio", "fluido"], render_static_abstract_prism),
    StaticSpec("space-carina-drift", "Carina Nebula", "space", 95, 1763300000000, ["nebulosa", "estrellas", "galaxia"], render_static_space_carina),
    StaticSpec("nature-emerald-canopy", "Emerald Canopy", "nature", 89, 1763200000000, ["bosque", "niebla", "verde"], render_static_nature_canopy),
    StaticSpec("water-abyssal-caustics", "Abyssal Caustics", "water", 87, 1763100000000, ["agua", "oceano", "turquesa"], render_static_water_caustics),
    StaticSpec("colors-chroma-bloom", "Chroma Bloom", "colors", 91, 1763000000000, ["colores", "degradado", "vivo"], render_static_colors_bloom),
    StaticSpec("minimal-monolith-sand", "Monolith Sand", "minimal", 84, 1762900000000, ["bauhaus", "simple", "arena"], render_static_minimal_monolith),
    StaticSpec("landscapes-alpine-dusk", "Alpine Dusk", "landscapes", 93, 1762800000000, ["montanas", "atardecer", "luna"], render_static_landscapes_alpine),
    StaticSpec("anime-sakura-torii", "Sakura Torii", "anime", 96, 1762700000000, ["anime", "sakura", "torii", "japon"], render_static_anime_torii),
]

VIDEO_SPECS = [
    VideoSpec("amoled-crimson-pulse", "Crimson Pulse (Live)", "amoled", 99, 1763600000000, ["amoled", "live", "rojo", "negro"], render_video_frame_amoled_pulse),
    VideoSpec("abstract-kinetic-silk", "Kinetic Silk (Live)", "abstract", 94, 1763550000000, ["seda", "live", "fluido"], render_video_frame_kinetic_silk),
    VideoSpec("space-orbit-horizon", "Orbit Horizon (Live)", "space", 97, 1763450000000, ["planeta", "orbita", "espacio", "live"], render_video_frame_space_orbit),
    VideoSpec("nature-firefly-glade", "Firefly Glade (Live)", "nature", 88, 1763350000000, ["luciernagas", "bosque", "noche", "live"], render_video_frame_nature_fireflies),
    VideoSpec("water-biolume-tide", "Biolume Tide (Live)", "water", 90, 1763250000000, ["agua", "bioluminiscente", "mar", "live"], render_video_frame_water_biolume),
    VideoSpec("colors-spectral-flow", "Spectral Flow (Live)", "colors", 92, 1763150000000, ["espectro", "colores", "live"], render_video_frame_colors_spectral),
    VideoSpec("minimal-zen-eclipse", "Zen Eclipse (Live)", "minimal", 86, 1763050000000, ["eclipse", "minimal", "live"], render_video_frame_minimal_eclipse),
    VideoSpec("landscapes-aurora-fjord", "Aurora Fjord (Live)", "landscapes", 95, 1762950000000, ["aurora", "fiordo", "noche", "live"], render_video_frame_landscapes_aurora),
    VideoSpec("anime-starlight-petals", "Starlight Petals (Live)", "anime", 97, 1762850000000, ["anime", "sakura", "estrellas", "live"], render_video_frame_anime_petals),
]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--docs-dir", default="docs", help="Output directory for GitHub Pages (catalog.json + thumbs)")
    parser.add_argument("--assets-dir", default="/tmp/catalog_release_assets", help="Output directory for heavy GitHub Release assets")
    parser.add_argument("--app-fallback", default="app/src/main/assets/catalog_fallback.json", help="Bundled fallback catalog path")
    args = parser.parse_args()

    docs_dir = Path(args.docs_dir)
    thumbs_dir = docs_dir / "thumbs"
    assets_dir = Path(args.assets_dir)
    docs_dir.mkdir(parents=True, exist_ok=True)
    thumbs_dir.mkdir(parents=True, exist_ok=True)
    assets_dir.mkdir(parents=True, exist_ok=True)

    items = []

    for spec in STATIC_SPECS:
        print(f"Rendering static {spec.id}...")
        img = spec.render(1080, 2400)
        full_file = assets_dir / f"{spec.id}.webp"
        thumb_file = thumbs_dir / f"{spec.id}.webp"
        img.save(full_file, "WEBP", quality=92, method=6)
        img.resize((360, 800), Image.Resampling.LANCZOS).save(thumb_file, "WEBP", quality=82, method=6)

        items.append({
            "id": spec.id,
            "title": spec.title,
            "author": "Kromiv Originals",
            "license": "CC0 1.0",
            "category": spec.category,
            "mediaType": "image",
            "format": "webp",
            "thumbnailUrl": f"{PAGES_BASE}/thumbs/{spec.id}.webp",
            "previewUrl": None,
            "downloadUrl": f"{RELEASE_BASE}/{full_file.name}",
            "width": 1080,
            "height": 2400,
            "durationMs": 0,
            "bitrateBps": 0,
            "fps": 0.0,
            "fileSizeBytes": full_file.stat().st_size,
            "sha256": sha256_file(full_file),
            "popularity": spec.popularity,
            "createdAtEpochMs": spec.created_ms,
            "tags": spec.tags,
        })

    for spec in VIDEO_SPECS:
        print(f"Rendering live video {spec.id}...")
        full_file = assets_dir / f"{spec.id}.mp4"
        preview_file = assets_dir / f"{spec.id}-preview.mp4"
        thumb_file = thumbs_dir / f"{spec.id}.webp"
        encode_loop_mp4(spec.render_frame, full_file, preview_file, thumb_file)

        items.append({
            "id": spec.id,
            "title": spec.title,
            "author": "Kromiv Originals",
            "license": "CC0 1.0",
            "category": spec.category,
            "mediaType": "video",
            "format": "mp4",
            "thumbnailUrl": f"{PAGES_BASE}/thumbs/{spec.id}.webp",
            "previewUrl": f"{RELEASE_BASE}/{preview_file.name}",
            "downloadUrl": f"{RELEASE_BASE}/{full_file.name}",
            "width": 1080,
            "height": 1920,
            "durationMs": 4000,
            "bitrateBps": int(full_file.stat().st_size * 8 / 4),
            "fps": 30.0,
            "fileSizeBytes": full_file.stat().st_size,
            "sha256": sha256_file(full_file),
            "popularity": spec.popularity,
            "createdAtEpochMs": spec.created_ms,
            "tags": spec.tags,
        })

    manifest = {
        "version": 1,
        "updatedAt": "2026-11-22",
        "baseAssetsUrl": RELEASE_BASE,
        "items": items,
    }

    catalog_json = json.dumps(manifest, indent=2, ensure_ascii=False)
    (docs_dir / "catalog.json").write_text(catalog_json, encoding="utf-8")

    fallback_path = Path(args.app_fallback)
    fallback_path.parent.mkdir(parents=True, exist_ok=True)
    fallback_path.write_text(catalog_json, encoding="utf-8")
    print(f"Generated {len(items)} catalog items into {docs_dir} and {assets_dir}")


if __name__ == "__main__":
    main()
