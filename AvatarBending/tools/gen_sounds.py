"""Synthesizes every custom sound effect of the Avatar Bending mod.

All sounds are made from scratch (noise, oscillators, filters, envelopes and reverb), so they are
free to use. Output: mono 44.1 kHz OGG Vorbis files in assets/avatarbending/sounds/, plus
sounds.json.

Run from the AvatarBending folder:  python3 tools/gen_sounds.py
Requires numpy, scipy and ffmpeg (with libvorbis).
"""
import json
import math
import os
import subprocess
import tempfile
import wave

import numpy as np
from scipy.signal import butter, fftconvolve, lfilter, sosfilt

SR = 44100
ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "avatarbending")
OUT = os.path.join(ROOT, "sounds")
rng = np.random.default_rng(1337)


# --------------------------------------------------------------------------- building blocks

def n_of(seconds):
    return int(SR * seconds)


def times(n):
    return np.arange(n) / SR


def white(n):
    return rng.standard_normal(n)


def pink(n):
    # Paul Kellet's economy pink filter.
    b = [0.049922035, -0.095993537, 0.050612699, -0.004408786]
    a = [1, -2.494956002, 2.017265875, -0.522189400]
    x = lfilter(b, a, white(n))
    return x / (np.std(x) + 1e-9)


def brown(n):
    x = np.cumsum(white(n))
    x = filt(x, "highpass", 20)
    return x / (np.std(x) + 1e-9)


def filt(x, kind, freq, order=2):
    sos = butter(order, freq, btype=kind, fs=SR, output="sos")
    return sosfilt(sos, x)


def curve(n, points, shape="exp"):
    """Piecewise curve through (time_fraction, value) points. 'exp' interpolates geometrically."""
    xs = np.linspace(0, 1, n)
    px = [p[0] for p in points]
    py = [p[1] for p in points]
    if shape == "exp":
        return np.exp(np.interp(xs, px, np.log(np.maximum(py, 1e-6))))
    return np.interp(xs, px, py)


def svf(x, cutoff, q=0.7, mode="band"):
    """Time-varying state variable filter (Cytomic TPT). cutoff may be a scalar or an array."""
    n = len(x)
    fc = np.broadcast_to(np.asarray(cutoff, dtype=float), (n,))
    fc = np.clip(fc, 20, SR * 0.45)
    g = np.tan(np.pi * fc / SR)
    k = 1.0 / q
    a1 = 1.0 / (1.0 + g * (g + k))
    a2 = g * a1
    a3 = g * a2
    out = np.empty(n)
    ic1 = ic2 = 0.0
    for i in range(n):
        v0 = x[i]
        v3 = v0 - ic2
        v1 = a1[i] * ic1 + a2[i] * v3
        v2 = ic2 + a2[i] * ic1 + a3[i] * v3
        ic1 = 2 * v1 - ic1
        ic2 = 2 * v2 - ic2
        if mode == "band":
            out[i] = v1
        elif mode == "low":
            out[i] = v2
        else:
            out[i] = v0 - k * v1 - v2
    return out


def env_ad(n, attack, decay_shape=4.0, hold=0.0):
    """Fast attack (seconds) then exponential-ish decay over the rest."""
    t = times(n)
    a = n_of(attack)
    e = np.ones(n)
    if a > 0:
        e[:a] = np.sin(np.linspace(0, np.pi / 2, a)) ** 2
    start = a + n_of(hold)
    if start < n:
        rest = np.linspace(0, 1, n - start)
        e[start:] = np.exp(-decay_shape * rest) * (1 - rest) ** 0.5
    return e


def env_points(n, points):
    return curve(n, points, shape="lin")


def sine(freq, n, phase=0.0):
    f = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    ph = 2 * np.pi * np.cumsum(f) / SR + phase
    return np.sin(ph)


def saw(freq, n):
    f = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    ph = np.cumsum(f) / SR + rng.random()
    return 2.0 * (ph % 1.0) - 1.0


def impulses(n, rate, length=0.004, band=(1500, 7000), decay=None):
    """Random crackles: short noise bursts at a (possibly varying) rate per second."""
    out = np.zeros(n)
    r = np.broadcast_to(np.asarray(rate, dtype=float), (n,))
    prob = r / SR
    hits = np.nonzero(rng.random(n) < prob)[0]
    blen = max(8, n_of(length))
    for h in hits:
        seg = white(blen) * np.exp(-np.linspace(0, 6, blen)) * rng.uniform(0.3, 1.0)
        end = min(n, h + blen)
        out[h:end] += seg[: end - h]
    out = filt(out, "bandpass", band)
    if decay is not None:
        out *= decay
    return out


def bubbles(n, rate, fmin=300, fmax=1200):
    out = np.zeros(n)
    hits = np.nonzero(rng.random(n) < rate / SR)[0]
    for h in hits:
        dur = rng.uniform(0.01, 0.04)
        m = n_of(dur)
        f0 = rng.uniform(fmin, fmax)
        f = np.linspace(f0, f0 * rng.uniform(1.6, 2.6), m)
        seg = sine(f, m) * np.sin(np.linspace(0, np.pi, m)) * rng.uniform(0.2, 0.8)
        end = min(n, h + m)
        out[h:end] += seg[: end - h]
    return out


def reverb(x, seconds=1.2, mix=0.25, brightness=5000, predelay=0.02):
    m = n_of(seconds)
    t = times(m)
    ir = white(m) * np.exp(-6.9 * t / seconds)
    ir = filt(ir, "lowpass", brightness)
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    pad = n_of(predelay)
    wet = fftconvolve(np.concatenate([np.zeros(pad), x]), ir)
    out = np.concatenate([x, np.zeros(len(wet) - len(x))])
    out += mix * wet * (np.std(x) / (np.std(wet) + 1e-9))
    return out


def soft_clip(x, drive=1.5):
    return np.tanh(x * drive) / np.tanh(drive)


def fade(x, fade_in=0.004, fade_out=0.05):
    a = n_of(fade_in)
    b = n_of(fade_out)
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b) ** 2
    return x


def trim_silence(x, threshold=0.0015):
    idx = np.nonzero(np.abs(x) > threshold * np.max(np.abs(x)) * 10)[0]
    if len(idx) == 0:
        return x
    return x[: min(len(x), idx[-1] + n_of(0.05))]


def finish(x, peak=0.89, drive=None):
    x = np.nan_to_num(x)
    x = x - np.mean(x)
    x = filt(x, "highpass", 25)
    if drive:
        x = soft_clip(x / (np.max(np.abs(x)) + 1e-9), drive)
    x = trim_silence(x)
    x = fade(x)
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def mix(*layers):
    n = max(len(l) for l, _ in layers)
    out = np.zeros(n)
    for layer, gain in layers:
        out[: len(layer)] += layer * gain
    return out


def at(x, seconds, total):
    out = np.zeros(total)
    s = n_of(seconds)
    end = min(total, s + len(x))
    out[s:end] = x[: end - s]
    return out


# --------------------------------------------------------------------------- sound designs

def air_whoosh(seed):
    global rng
    rng = np.random.default_rng(seed)
    dur = rng.uniform(0.6, 0.85)
    n = n_of(dur)
    peak_f = rng.uniform(1700, 2600)
    body = svf(pink(n), curve(n, [(0, 380), (0.33, peak_f), (1, 650)]), q=1.3)
    hiss = filt(white(n), "highpass", 3500) * env_ad(n, 0.12, 6)
    e = env_points(n, [(0, 0), (0.08, 0.5), (0.32, 1.0), (1, 0)]) ** 1.4
    thump = sine(curve(n_of(0.14), [(0, 75), (1, 42)]), n_of(0.14)) * env_ad(n_of(0.14), 0.004, 5)
    x = mix((body * e, 1.0), (hiss * e, 0.25), (thump, 0.35))
    return finish(reverb(x, 0.7, 0.15))


def air_blast_big():
    n = n_of(1.5)
    body = svf(pink(n), curve(n, [(0, 220), (0.25, 1300), (1, 260)]), q=1.1)
    body2 = svf(pink(n), curve(n, [(0, 500), (0.3, 3200), (1, 700)]), q=1.6)
    e = env_points(n, [(0, 0), (0.05, 0.7), (0.22, 1.0), (1, 0)]) ** 1.6
    boom_n = n_of(0.6)
    boom = sine(curve(boom_n, [(0, 62), (1, 30)]), boom_n) * env_ad(boom_n, 0.005, 4)
    x = mix((body * e, 1.0), (body2 * e, 0.45), (boom, 0.8))
    return finish(reverb(x, 1.6, 0.3, 3500), drive=1.6)


def air_slash(seed):
    global rng
    rng = np.random.default_rng(seed)
    n = n_of(0.42)
    body = svf(white(n), curve(n, [(0, 1200), (0.25, rng.uniform(5500, 7500)), (1, 2000)]), q=2.2)
    e = env_points(n, [(0, 0), (0.06, 1.0), (1, 0)]) ** 2.2
    x = body * e
    return finish(reverb(x, 0.5, 0.12, 8000))


def wind_howl():
    n = n_of(2.8)
    t = times(n)
    lfo = 0.5 + 0.5 * np.sin(2 * np.pi * 0.42 * t + 1.0)
    center = 260 + 620 * lfo
    howl = svf(mix((brown(n), 0.6), (pink(n), 0.6)), center, q=3.0)
    rumble = filt(brown(n), "lowpass", 160)
    am = 0.75 + 0.25 * np.sin(2 * np.pi * 0.9 * t) * np.sin(2 * np.pi * 0.31 * t + 2)
    e = env_points(n, [(0, 0), (0.15, 1), (0.8, 1), (1, 0)])
    x = mix((howl * am * e, 1.0), (rumble * e, 0.5))
    return finish(reverb(x, 1.4, 0.25, 3000))


def fire_whoosh(seed):
    global rng
    rng = np.random.default_rng(seed)
    dur = rng.uniform(0.7, 0.95)
    n = n_of(dur)
    noise = mix((brown(n), 0.7), (pink(n), 0.6))
    body = svf(noise, curve(n, [(0, 500), (0.2, rng.uniform(2800, 3800)), (1, 700)]), q=0.8, mode="low")
    e = env_points(n, [(0, 0), (0.05, 1.0), (0.3, 0.75), (1, 0)]) ** 1.3
    crackle = impulses(n, curve(n, [(0, 25), (1, 60)]), 0.003, (1500, 7000)) * env_ad(n, 0.05, 3)
    fwump_n = n_of(0.18)
    fwump = sine(curve(fwump_n, [(0, 95), (1, 40)]), fwump_n) * env_ad(fwump_n, 0.004, 4)
    x = mix((body * e, 1.0), (crackle, 0.9), (fwump, 0.5))
    return finish(reverb(x, 0.8, 0.18, 4500))


def fire_roar():
    n = n_of(2.2)
    t = times(n)
    flicker = 0.7 + 0.3 * np.abs(np.sin(2 * np.pi * 7.3 * t) * np.sin(2 * np.pi * 3.1 * t + 0.5))
    body = svf(mix((brown(n), 0.8), (pink(n), 0.5)), curve(n, [(0, 400), (0.25, 1800), (1, 600)]), q=0.7, mode="low")
    e = env_points(n, [(0, 0), (0.1, 1), (0.55, 0.8), (1, 0)])
    crackle = impulses(n, 70, 0.004, (1200, 6500)) * e
    sub = sine(42, n) * e * 0.6
    x = mix((body * flicker * e, 1.0), (crackle, 1.0), (sub, 0.4))
    return finish(reverb(x, 1.3, 0.25, 4000), drive=1.3)


def fire_explosion():
    n = n_of(2.4)
    boom_n = n_of(1.0)
    boom = sine(curve(boom_n, [(0, 115), (0.3, 50), (1, 28)]), boom_n) * env_ad(boom_n, 0.003, 3.5)
    burst = svf(white(n), curve(n, [(0, 4000), (0.15, 1200), (1, 250)]), q=0.7, mode="low") * env_ad(n, 0.002, 5)
    crackle = impulses(n, curve(n, [(0, 120), (1, 10)]), 0.004, (1000, 6000)) * env_points(n, [(0, 0), (0.05, 1), (1, 0)])
    click = white(n_of(0.01)) * np.linspace(1, 0, n_of(0.01))
    x = mix((boom, 1.0), (burst, 0.9), (crackle, 0.6), (click, 0.6))
    return finish(reverb(x, 1.8, 0.3, 3000), drive=2.0)


def electric_charge():
    n = n_of(1.5)
    t = times(n)
    f = curve(n, [(0, 55), (1, 380)])
    buzz = saw(f, n) * (0.55 + 0.45 * np.sign(np.sin(2 * np.pi * curve(n, [(0, 18), (1, 45)]) * t)))
    buzz = filt(buzz, "bandpass", (150, 4500))
    whine = sine(curve(n, [(0, 300), (1, 2200)]), n) * 0.25
    crackle = impulses(n, curve(n, [(0, 4), (1, 110)]), 0.006, (2000, 9000))
    e = curve(n, [(0, 0.08), (0.85, 1.0), (1, 1.0)])
    x = mix((buzz * e, 0.7), (whine * e, 0.5), (crackle * e, 1.4))
    return finish(reverb(x, 0.8, 0.2, 7000), drive=1.8)


def lightning_strike():
    n = n_of(3.0)
    crack = np.zeros(n)
    for _ in range(7):
        s = n_of(rng.uniform(0, 0.08))
        m = n_of(rng.uniform(0.004, 0.02))
        crack[s:s + m] += white(m) * np.exp(-np.linspace(0, 5, m)) * rng.uniform(0.5, 1)
    crack = filt(crack, "highpass", 300)
    rumble = filt(brown(n), "lowpass", 420)
    rumble_env = env_ad(n, 0.04, 2.2) * (0.7 + 0.3 * filt(np.abs(white(n)), "lowpass", 6) * 3)
    sub_n = n_of(1.2)
    sub = sine(curve(sub_n, [(0, 55), (1, 28)]), sub_n) * env_ad(sub_n, 0.005, 3)
    sizzle = impulses(n, curve(n, [(0, 200), (0.3, 20), (1, 0.1)]), 0.003, (3000, 10000))
    x = mix((crack, 1.4), (rumble * rumble_env, 1.0), (sub, 0.9), (sizzle, 0.7))
    return finish(reverb(x, 2.2, 0.35, 2500), drive=1.8)


def water_whoosh(seed):
    global rng
    rng = np.random.default_rng(seed)
    dur = rng.uniform(0.65, 0.9)
    n = n_of(dur)
    t = times(n)
    body = svf(pink(n), curve(n, [(0, 500), (0.3, rng.uniform(2000, 2900)), (1, 800)]), q=1.0)
    wobble = 1 + 0.5 * np.sin(2 * np.pi * (rng.uniform(22, 40) + 6 * np.sin(2 * np.pi * 3 * t)) * t)
    e = env_points(n, [(0, 0), (0.1, 1), (1, 0)]) ** 1.3
    bub = bubbles(n, 55, 350, 1100) * e
    splash = filt(white(n_of(0.2)), "highpass", 1500) * env_ad(n_of(0.2), 0.005, 5)
    x = mix((body * wobble * e, 1.0), (bub, 0.6), (at(splash, dur * 0.65, n), 0.5))
    return finish(reverb(x, 0.8, 0.2, 5000))


def water_surge():
    n = n_of(2.4)
    body = svf(mix((pink(n), 0.7), (brown(n), 0.6)), curve(n, [(0, 300), (0.45, 1600), (1, 500)]), q=0.8, mode="low")
    e = env_points(n, [(0, 0), (0.4, 1.0), (0.55, 0.8), (1, 0)])
    bub = bubbles(n, 90, 250, 900) * e
    crash_n = n_of(0.9)
    crash = filt(white(crash_n), "highpass", 900) * env_ad(crash_n, 0.01, 3.5)
    x = mix((body * e, 1.0), (bub, 0.5), (at(crash, 0.95, n), 0.8))
    return finish(reverb(x, 1.5, 0.28, 4500))


def ice_crack(seed):
    global rng
    rng = np.random.default_rng(seed)
    n = n_of(0.65)
    clicks = np.zeros(n)
    for _ in range(rng.integers(3, 7)):
        s = n_of(rng.uniform(0, 0.12))
        m = n_of(0.002)
        clicks[s:s + m] += white(m) * rng.uniform(0.6, 1)
    clicks = filt(clicks, "highpass", 1000)
    ring = np.zeros(n)
    for f in (2100, 3400, 5200, 7900):
        f *= rng.uniform(0.9, 1.1)
        ring += sine(f, n, rng.random() * 6) * np.exp(-times(n) / rng.uniform(0.06, 0.16)) * rng.uniform(0.3, 1)
    crunch = filt(white(n), "bandpass", (2000, 8000)) * env_ad(n, 0.002, 18)
    x = mix((clicks, 1.2), (ring, 0.35), (crunch, 0.8))
    return finish(reverb(x, 0.6, 0.18, 9000), drive=3.0)


def rock_rumble():
    n = n_of(1.9)
    t = times(n)
    low = filt(brown(n), "lowpass", 180)
    grit = filt(pink(n), "bandpass", (300, 900))
    hits = filt(np.abs(white(n)) ** 3, "lowpass", 14)
    hits /= np.max(hits) + 1e-9
    sub = sine(38 + 4 * np.sin(2 * np.pi * 0.7 * t), n)
    e = env_points(n, [(0, 0), (0.1, 1), (0.7, 0.8), (1, 0)])
    x = mix((low * e, 1.0), (grit * (0.4 + hits) * e, 0.5), (sub * e, 0.5))
    return finish(reverb(x, 1.4, 0.25, 2500), drive=1.4)


def rock_impact(seed):
    global rng
    rng = np.random.default_rng(seed)
    n = n_of(0.85)
    th_n = n_of(0.2)
    thump = sine(curve(th_n, [(0, rng.uniform(85, 105)), (1, 42)]), th_n) * env_ad(th_n, 0.001, 4)
    crunch = filt(white(n), "bandpass", (250, 2600)) * env_ad(n, 0.002, 9)
    debris = impulses(n, curve(n, [(0, 90), (1, 3)]), 0.005, (600, 4000)) * env_points(n, [(0, 0), (0.1, 1), (1, 0)])
    x = mix((thump, 1.0), (crunch, 0.8), (debris, 0.7))
    return finish(reverb(x, 0.7, 0.18, 4000), drive=1.6)


def quake_boom():
    n = n_of(2.8)
    sub_n = n_of(1.8)
    sub = sine(curve(sub_n, [(0, 64), (1, 22)]), sub_n) * env_ad(sub_n, 0.004, 2.6)
    rumble = filt(brown(n), "lowpass", 300) * env_ad(n, 0.02, 2.4)
    crack = filt(white(n_of(0.05)), "highpass", 400) * np.linspace(1, 0, n_of(0.05))
    debris = impulses(n, curve(n, [(0, 60), (1, 2)]), 0.006, (500, 3500)) * env_points(n, [(0, 0), (0.08, 1), (1, 0)])
    x = mix((sub, 1.0), (rumble, 0.9), (crack, 0.6), (debris, 0.6))
    return finish(reverb(x, 2.0, 0.3, 2200), drive=2.2)


def charge_magic():
    n = n_of(1.5)
    t = times(n)
    root = curve(n, [(0, 196), (1, 392)])
    chord = np.zeros(n)
    for i, ratio in enumerate((1, 1.5, 2, 2.5, 3)):
        trem = 0.7 + 0.3 * np.sin(2 * np.pi * (8 + i * 1.3) * t + i)
        chord += sine(root * ratio * (1 + 0.003 * i), n, i) * trem / (1 + i * 0.5)
    sparkle = np.zeros(n)
    hits = np.nonzero(rng.random(n) < curve(n, [(0, 5), (1, 60)]) / SR)[0]
    for h in hits:
        m = n_of(0.035)
        seg = sine(rng.uniform(2500, 6500), m) * np.exp(-np.linspace(0, 6, m))
        end = min(n, h + m)
        sparkle[h:end] += seg[: end - h]
    air = svf(pink(n), curve(n, [(0, 800), (1, 6000)]), q=1.5)
    e = curve(n, [(0, 0.05), (0.9, 1.0), (1, 0.9)])
    x = mix((chord * e, 0.9), (sparkle * e, 0.35), (air * e, 0.3))
    return finish(reverb(x, 1.2, 0.3, 7000))


def bell(freq, n, decay=1.6):
    out = np.zeros(n)
    t = times(n)
    for ratio, amp, d in ((1, 1, decay), (2.0, 0.5, decay * 0.6), (2.76, 0.35, decay * 0.4), (5.4, 0.18, decay * 0.2), (8.9, 0.08, decay * 0.12)):
        out += sine(freq * ratio, n, rng.random() * 6) * amp * np.exp(-t / d)
    attack = n_of(0.003)
    out[:attack] *= np.linspace(0, 1, attack)
    return out


def spirit_chime():
    n = n_of(2.6)
    x = np.zeros(n)
    for i, f in enumerate((880, 1108.7, 1318.5, 1760)):
        x += at(bell(f, n_of(2.2), 1.4), 0.09 * i, n) * (1 - i * 0.12)
    pad = sine(440, n) * env_points(n, [(0, 0), (0.3, 1), (1, 0)]) * 0.25
    return finish(reverb(mix((x, 1.0), (pad, 1.0)), 2.4, 0.42, 8000))


def avatar_state():
    n = n_of(5.2)
    t = times(n)
    # Choir: detuned saw voices through "ah" formants.
    voices = np.zeros(n)
    for f in (110.0, 164.81, 220.0, 277.18, 329.63, 440.0):
        for detune in (-0.004, 0.0, 0.004):
            vib = 1 + 0.003 * np.sin(2 * np.pi * (4.8 + rng.random()) * t + rng.random() * 6)
            voices += saw(f * (1 + detune) * vib, n)
    choir = (svf(voices, 730, 6, "band") * 1.0 + svf(voices, 1090, 8, "band") * 0.55
             + svf(voices, 2440, 10, "band") * 0.28 + filt(voices, "lowpass", 400) * 0.25)
    choir_env = env_points(n, [(0, 0), (0.1, 0.9), (0.62, 1.0), (1, 0)])
    shimmer = (sine(659.25, n) + sine(880, n, 1) * 0.8 + sine(1108.7, n, 2) * 0.5) * (0.6 + 0.4 * np.sin(2 * np.pi * 6 * t))
    shimmer *= env_points(n, [(0, 0), (0.12, 0), (0.3, 1), (0.7, 0.8), (1, 0)])
    boom_n = n_of(1.4)
    boom = sine(curve(boom_n, [(0, 60), (1, 26)]), boom_n) * env_ad(boom_n, 0.004, 3)
    burst = filt(white(n_of(1.0)), "lowpass", 1200) * env_ad(n_of(1.0), 0.003, 6)
    crack = filt(white(n_of(0.04)), "highpass", 500) * np.linspace(1, 0, n_of(0.04))
    rise = svf(pink(n), curve(n, [(0, 200), (0.2, 2500), (1, 300)]), q=1.2) * env_points(n, [(0, 0.6), (0.25, 0.4), (1, 0)])
    x = mix((choir * choir_env, 1.0), (shimmer, 0.12), (boom, 1.1), (burst, 0.5), (crack, 0.4), (rise, 0.35))
    return finish(reverb(x, 3.0, 0.42, 4000), drive=1.5)


def avatar_pulse():
    n = n_of(1.3)
    t = times(n)
    e = env_points(n, [(0, 0), (0.2, 1), (1, 0)]) ** 1.5
    low = sine(68, n) * e
    swell = filt(pink(n), "lowpass", 600) * e
    chord = (sine(440, n) + sine(659.25, n, 1) * 0.7) * e * (0.6 + 0.4 * np.sin(2 * np.pi * 5 * t))
    x = mix((low, 1.0), (swell, 0.5), (chord, 0.12))
    return finish(reverb(x, 1.6, 0.35, 3000))


def energy_beam():
    n = n_of(2.6)
    t = times(n)
    tone = saw(110, n) + saw(110.7, n) + saw(165.2, n) * 0.7 + sine(55, n) * 1.5
    tone = filt(tone, "lowpass", 1800)
    buzz = 0.8 + 0.2 * np.sin(2 * np.pi * 31 * t)
    wobble = 0.85 + 0.15 * np.sin(2 * np.pi * 6.5 * t)
    hiss = filt(white(n), "bandpass", (4000, 9000))
    e = env_points(n, [(0, 0), (0.03, 1), (0.85, 1), (1, 0)])
    x = mix((tone * buzz * wobble * e, 1.0), (hiss * e, 0.12))
    return finish(reverb(x, 0.9, 0.2, 5000), drive=1.3)


def metal_zing():
    n = n_of(0.8)
    t = times(n)
    ring = np.zeros(n)
    for f, d in ((1180, 0.45), (2950, 0.3), (4720, 0.22), (7300, 0.15)):
        ring += sine(f * curve(n, [(0, 1.0), (1, 0.92)]), n, rng.random() * 6) * np.exp(-t / d)
    rattle = filt(white(n), "bandpass", (3000, 8000)) * (np.sin(2 * np.pi * 28 * t) > 0.2) * env_ad(n, 0.002, 7)
    click = white(n_of(0.004))
    x = mix((ring, 0.6), (rattle, 0.7), (click, 0.8))
    return finish(reverb(x, 0.8, 0.22, 9000))


def whip_crack(seed):
    global rng
    rng = np.random.default_rng(seed)
    n = n_of(0.4)
    crack = np.zeros(n)
    s = n_of(0.08)
    m = n_of(0.006)
    crack[s:s + m] = white(m) * np.exp(-np.linspace(0, 4, m))
    crack = filt(crack, "highpass", 800)
    swish = svf(white(n), curve(n, [(0, 800), (0.2, 5000), (1, 1500)]), q=2) * env_points(n, [(0, 0), (0.18, 1), (0.25, 0.3), (1, 0)])
    x = mix((crack, 2.0), (swish, 0.6))
    return finish(reverb(x, 0.5, 0.15, 9000), drive=3.5)


# --------------------------------------------------------------------------- export

SOUNDS = {
    "air_whoosh": [lambda: air_whoosh(11), lambda: air_whoosh(12), lambda: air_whoosh(13)],
    "air_blast": [air_blast_big],
    "air_slash": [lambda: air_slash(21), lambda: air_slash(22)],
    "wind_howl": [wind_howl],
    "fire_whoosh": [lambda: fire_whoosh(31), lambda: fire_whoosh(32), lambda: fire_whoosh(33)],
    "fire_roar": [fire_roar],
    "fire_explosion": [fire_explosion],
    "electric_charge": [electric_charge],
    "lightning_strike": [lightning_strike],
    "water_whoosh": [lambda: water_whoosh(41), lambda: water_whoosh(42), lambda: water_whoosh(43)],
    "water_surge": [water_surge],
    "ice_crack": [lambda: ice_crack(51), lambda: ice_crack(52), lambda: ice_crack(53)],
    "rock_rumble": [rock_rumble],
    "rock_impact": [lambda: rock_impact(61), lambda: rock_impact(62), lambda: rock_impact(63)],
    "quake_boom": [quake_boom],
    "charge_magic": [charge_magic],
    "spirit_chime": [spirit_chime],
    "avatar_state": [avatar_state],
    "avatar_pulse": [avatar_pulse],
    "energy_beam": [energy_beam],
    "metal_zing": [metal_zing],
    "whip_crack": [lambda: whip_crack(71), lambda: whip_crack(72)],
}

SUBTITLES = {
    "air_whoosh": "Air whooshes",
    "air_blast": "Air cannon booms",
    "air_slash": "Air blade slices",
    "wind_howl": "Wind howls",
    "fire_whoosh": "Fire whooshes",
    "fire_roar": "Fire roars",
    "fire_explosion": "Fire explodes",
    "electric_charge": "Lightning charges",
    "lightning_strike": "Lightning strikes",
    "water_whoosh": "Water rushes",
    "water_surge": "Wave crashes",
    "ice_crack": "Ice cracks",
    "rock_rumble": "Earth rumbles",
    "rock_impact": "Rock smashes",
    "quake_boom": "Earth quakes",
    "charge_magic": "Energy gathers",
    "spirit_chime": "Spirit chimes",
    "avatar_state": "The Avatar State awakens",
    "avatar_pulse": "Avatar aura pulses",
    "energy_beam": "Energy beam hums",
    "metal_zing": "Metal cable zings",
    "whip_crack": "Whip cracks",
}


def write_ogg(path, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = tmp.name
    with wave.open(wav_path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-ac", "1", "-ar", str(SR),
                    "-c:a", "libvorbis", "-q:a", "5", path], check=True)
    os.unlink(wav_path)


def main():
    os.makedirs(OUT, exist_ok=True)
    sounds_json = {}
    report = []
    for name, makers in SOUNDS.items():
        files = []
        for i, make in enumerate(makers):
            x = make()
            file_name = f"{name}{i + 1}" if len(makers) > 1 else name
            write_ogg(os.path.join(OUT, file_name + ".ogg"), x)
            files.append({"name": f"avatarbending:{file_name}"})
            rms = 20 * math.log10(np.sqrt(np.mean(x ** 2)) + 1e-9)
            report.append(f"{file_name:22s} {len(x) / SR:5.2f}s  rms {rms:6.1f} dBFS")
        sounds_json[name] = {"sounds": files, "subtitle": f"subtitles.avatarbending.{name}"}
    with open(os.path.join(ROOT, "sounds.json"), "w") as f:
        json.dump(sounds_json, f, indent=2)
    print("\n".join(report))
    print("Wrote", len(report), "files to", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
