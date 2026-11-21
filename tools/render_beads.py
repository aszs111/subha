"""يولّد صور الخرز الواقعية (خرزة/فاصلة/مئذنة) لكل نوع بإظلال وملمس إجرائي.

الاستعمال: python3 tools/render_beads.py   (يكتب إلى app/src/main/res/drawable-nodpi)
"""
import os
import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res', 'drawable-nodpi')
SS = 3          # تنعيم الحواف
W = 220         # عرض صورة الخرزة النهائي

# ───────── ضجيج ثلاثي الأبعاد ─────────
class Noise:
    def __init__(self, seed, n=64):
        r = np.random.default_rng(seed)
        self.n = n
        self.v = r.random((n, n, n)).astype(np.float32)

    def __call__(self, x, y, z):
        n = self.n
        xi, yi, zi = np.floor(x).astype(int), np.floor(y).astype(int), np.floor(z).astype(int)
        xf, yf, zf = x - xi, y - yi, z - zi
        u, v, w = [t * t * (3 - 2 * t) for t in (xf, yf, zf)]
        def g(a, b, c):
            return self.v[(xi + a) % n, (yi + b) % n, (zi + c) % n]
        x00 = g(0, 0, 0) * (1 - u) + g(1, 0, 0) * u
        x10 = g(0, 1, 0) * (1 - u) + g(1, 1, 0) * u
        x01 = g(0, 0, 1) * (1 - u) + g(1, 0, 1) * u
        x11 = g(0, 1, 1) * (1 - u) + g(1, 1, 1) * u
        y0 = x00 * (1 - v) + x10 * v
        y1 = x01 * (1 - v) + x11 * v
        return y0 * (1 - w) + y1 * w

    def fbm(self, x, y, z, oct=4):
        s, a, f, tot = 0, 1.0, 1.0, 0
        for _ in range(oct):
            s = s + a * self(x * f + 17.3, y * f + 3.1, z * f + 9.7)
            tot += a; a *= 0.5; f *= 2.03
        return s / tot


def rot(seed):
    r = np.random.default_rng(seed)
    q = r.normal(size=4); q /= np.linalg.norm(q)
    a, b, c, d = q
    return np.array([[a*a+b*b-c*c-d*d, 2*(b*c-a*d), 2*(b*d+a*c)],
                     [2*(b*c+a*d), a*a-b*b+c*c-d*d, 2*(c*d-a*b)],
                     [2*(b*d-a*c), 2*(c*d+a*b), a*a-b*b-c*c+d*d]], dtype=np.float32)


def mix(a, b, t):
    t = np.clip(t, 0, 1)[..., None]
    return np.asarray(a, np.float32) * (1 - t) + np.asarray(b, np.float32) * t


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)

# ───────── الأشكال: تُرجع قناع، والناظم، ونقطة السطح ─────────

def ellipsoid(w, h, ax=1.0, ay=1.0, az=1.0):
    """ax, ay: نصفا القطرين في الصورة (نسبة إلى العرض/2)."""
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    x = (xs + 0.5 - w / 2) / (w / 2) / ax
    y = (ys + 0.5 - h / 2) / (w / 2) / ay
    r2 = x * x + y * y
    mask = r2 < 1
    z = np.sqrt(np.clip(1 - r2, 0, 1))
    n = np.stack([x / ax, y / ay, z / az], -1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True) + 1e-6
    p = np.stack([x * ax, y * ay, z * az], -1)
    return mask, n, p, (x, y, z)


def revolve(w, h, prof):
    """جسم دوراني رأسي؛ prof(t) نصف القطر عند t∈[0,1] من الأعلى للأسفل (نسبة إلى العرض/2)."""
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    x = (xs + 0.5 - w / 2) / (w / 2)
    t = (ys + 0.5) / h
    r = prof(t)
    dt = 1.0 / h
    dr = (prof(t + dt) - prof(t - dt)) / (2 * dt)        # dr/dt
    yscale = h / (w / 2)                                  # وحدات الصورة لكل t
    mask = np.abs(x) < r
    z = np.sqrt(np.clip(r * r - x * x, 0, None))
    n = np.stack([x, -r * dr / yscale, z], -1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True) + 1e-6
    yy = (t - 0.5) * yscale
    p = np.stack([x, yy, z], -1)
    return mask, n, p, (x, yy, z)

# ───────── الإضاءة ─────────
L = np.array([-0.45, -0.62, 0.65], np.float32); L /= np.linalg.norm(L)
V = np.array([0, 0, 1], np.float32)
H = (L + V); H /= np.linalg.norm(H)


def shade(mask, n, albedo, gloss=1.0, spec_col=(1, 1, 1), shin=90, translucent=None, rim=0.25, amb=0.22):
    ndl = np.clip(n @ L, 0, 1)
    diff = amb + 0.85 * ndl ** 0.9
    # ضوء مرتد خفيف من الأسفل
    diff += 0.12 * np.clip(n[..., 1], 0, 1)
    col = albedo * diff[..., None]
    col = col * (0.42 + 0.58 * np.clip(n[..., 2], 0, 1) ** 0.55)[..., None]   # تعتيم الحواف
    # لمعة حادة على هيئة نافذة (كما في الصور الحقيقية)
    ndh = np.clip(n @ H, 0, 1)
    spec = ndh ** shin
    # نافذة مستطيلة: قيّد اللمعة حسب الناظم
    win = smooth(0.0, 0.08, 0.16 - np.abs(n[..., 0] + 0.36)) * smooth(0.0, 0.1, 0.24 - np.abs(n[..., 1] + 0.42))
    spec = np.maximum(spec * 0.5, win * 0.95)
    col = col + gloss * spec[..., None] * np.array(spec_col, np.float32)
    # انعكاس حافة (فرينل)
    fres = (1 - np.clip(n[..., 2], 0, 1)) ** 3
    col = col + rim * gloss * fres[..., None] * 0.35
    return col


def to_img(col, mask, w, h):
    a = mask.astype(np.float32)
    rgba = np.concatenate([np.clip(col, 0, 1), a[..., None]], -1)
    rgba[~mask] = 0
    im = Image.fromarray((rgba * 255).astype(np.uint8), 'RGBA')
    return im.resize((w // SS, h // SS), Image.LANCZOS)

# ───────── المواد ─────────

def mat_olive(p, nz, seed):
    R = rot(seed); q = p @ R.T * 1.0
    warp = nz.fbm(q[..., 0] * 1.6, q[..., 1] * 1.6, q[..., 2] * 1.6, 4)
    d = np.sqrt((q[..., 0] + 0.35) ** 2 + (q[..., 2] * 0.7) ** 2) * 4.2 + warp * 3.0
    ring = np.abs(np.sin(d * np.pi))
    lines = smooth(0.90, 0.985, ring)
    fine = nz.fbm(q[..., 0] * 14, q[..., 1] * 3, q[..., 2] * 14, 3)
    base = mix((0.80, 0.58, 0.28), (0.92, 0.74, 0.42), fine)
    base = mix(base, (0.58, 0.36, 0.15), smooth(0.6, 0.92, ring) * 0.5)
    return mix(base, (0.20, 0.11, 0.05), lines * 0.92)


def mat_turq(p, nz, seed):
    R = rot(seed); q = p @ R.T
    blot = nz.fbm(q[..., 0] * 2.5, q[..., 1] * 2.5, q[..., 2] * 2.5, 4)
    base = mix((0.10, 0.55, 0.72), (0.30, 0.80, 0.90), blot)
    vn = nz.fbm(q[..., 0] * 3.2 + 40, q[..., 1] * 3.2, q[..., 2] * 3.2, 4)
    ridge = 1 - np.abs(vn - 0.5) * 2
    veins = smooth(0.955, 0.985, ridge) * smooth(0.45, 0.75, nz.fbm(q[..., 0] * 1.3 + 7, q[..., 1] * 1.3, q[..., 2] * 1.3, 2))
    return mix(base, (0.10, 0.09, 0.08), veins)


def mat_yusr(p, nz, seed):
    R = rot(seed); q = p @ R.T
    g = nz.fbm(q[..., 0] * 1.5, q[..., 1] * 9, q[..., 2] * 1.5, 3)
    return mix((0.03, 0.03, 0.03), (0.13, 0.10, 0.08), g * 0.8)


def mat_lapis(p, nz, seed):
    R = rot(seed); q = p @ R.T
    b = nz.fbm(q[..., 0] * 3, q[..., 1] * 3, q[..., 2] * 3, 4)
    base = mix((0.06, 0.12, 0.45), (0.18, 0.30, 0.78), b)
    streak = smooth(0.66, 0.8, nz.fbm(q[..., 0] * 2 + 3, q[..., 1] * 6, q[..., 2] * 2, 3))
    base = mix(base, (0.75, 0.78, 0.85), streak * 0.6)
    sp = nz(q[..., 0] * 40 + 1, q[..., 1] * 40, q[..., 2] * 40)
    return mix(base, (0.95, 0.80, 0.35), smooth(0.9, 0.95, sp))


def mat_pearl(p, nz, seed, n):
    t = n[..., 2]
    base = mix((0.86, 0.82, 0.76), (0.98, 0.96, 0.92), t)
    irid = 0.06 * np.stack([np.sin(t * 9), np.sin(t * 9 + 2), np.sin(t * 9 + 4)], -1)
    return base + irid


def mat_agate(p, nz, seed):
    R = rot(seed); q = p @ R.T
    w = nz.fbm(q[..., 0] * 1.5, q[..., 1] * 1.5, q[..., 2] * 1.5, 3)
    bands = np.sin((q[..., 1] * 3 + w * 3) * np.pi)
    base = mix((0.55, 0.06, 0.04), (0.85, 0.22, 0.10), bands * 0.5 + 0.5)
    return mix(base, (0.95, 0.80, 0.70), smooth(0.92, 0.99, bands) * 0.7)


def mat_jade(p, nz, seed):
    R = rot(seed); q = p @ R.T
    c = nz.fbm(q[..., 0] * 2.2, q[..., 1] * 2.2, q[..., 2] * 2.2, 4)
    return mix((0.05, 0.38, 0.22), (0.35, 0.72, 0.48), c)


def translucent(col_dark, col_mid, col_glow, xyz, p, nz, seed, clouds=0.25):
    """إظلال الكهرمان: يتوهج في داخله حيث يعبره الضوء."""
    x, y, z = xyz
    R = rot(seed); q = p @ R.T
    cl = nz.fbm(q[..., 0] * 2.2, q[..., 1] * 2.2, q[..., 2] * 2.2, 4)
    glow = np.exp(-(((x - 0.12) ** 2) / 0.25 + ((y - 0.32) ** 2) / 0.22))
    f = 0.25 + 0.45 * z + 0.65 * glow + clouds * (cl - 0.5)
    c = mix(col_dark, col_mid, smooth(0.0, 0.6, f))
    return mix(c, col_glow, smooth(0.55, 1.15, f))


STYLES = {
    # id: (shape, material)
    'olive':     dict(ax=1.0, ay=1.0, mat='olive', gloss=0.9, disc='woodred'),
    'amber':     dict(ax=0.88, ay=1.18, mat='amber', gloss=1.0, disc='same'),
    'turquoise': dict(ax=1.0, ay=1.0, mat='turq', gloss=1.0, disc='silver'),
    'yusr':      dict(ax=0.9, ay=1.08, mat='yusr', gloss=1.0, disc='silver'),
    'agate':     dict(ax=0.9, ay=1.1, mat='agate', gloss=1.0, disc='silver'),
    'lapis':     dict(ax=1.0, ay=1.0, mat='lapis', gloss=0.9, disc='gold'),
    'pearl':     dict(ax=1.0, ay=1.0, mat='pearl', gloss=0.7, disc='gold'),
    'jade':      dict(ax=1.0, ay=1.0, mat='jade', gloss=1.0, disc='gold'),
}


def albedo_for(st, p, n, xyz, seed, nz):
    m = st['mat']
    if m == 'olive': return mat_olive(p, nz, seed)
    if m == 'turq': return mat_turq(p, nz, seed)
    if m == 'yusr': return mat_yusr(p, nz, seed)
    if m == 'lapis': return mat_lapis(p, nz, seed)
    if m == 'pearl': return mat_pearl(p, nz, seed, n)
    if m == 'agate': return translucent((0.30, 0.02, 0.02), (0.70, 0.12, 0.06), (1.0, 0.55, 0.35), xyz, p, nz, seed) * 0.6 + mat_agate(p, nz, seed) * 0.4
    if m == 'jade': return translucent((0.02, 0.20, 0.10), (0.10, 0.50, 0.28), (0.55, 0.90, 0.60), xyz, p, nz, seed) * 0.6 + mat_jade(p, nz, seed) * 0.4
    if m == 'amber': return translucent((0.30, 0.04, 0.01), (0.82, 0.30, 0.03), (1.0, 0.70, 0.22), xyz, p, nz, seed, clouds=0.5)
    raise ValueError(m)


def render_bead(sid, st, var, nz):
    ax, ay = st['ax'], st['ay']
    w = W * SS
    h = int(w * ay / ax * 1.0)
    # المساحة: العرض يساوي قطر الخرزة الأفقي
    mask, n, p, xyz = ellipsoid(w, h, ax=1.0, ay=ay / ax)
    alb = albedo_for(st, p, n, xyz, 100 + var * 7 + sum(map(ord, sid)) % 50, nz)
    trans = st['mat'] in ('amber',)
    col = shade(mask, n, alb, gloss=st['gloss'], shin=120, amb=0.35 if trans else 0.22)
    if trans:  # الكهرمان أقل تأثرًا بالظل لأنه شفاف
        col = col * 0.8 + alb * 0.35
    return to_img(col, mask, w, h)


DISC_MATS = {
    'woodred': ((0.32, 0.08, 0.04), (0.62, 0.20, 0.10)),
    'silver': ((0.45, 0.47, 0.50), (0.92, 0.93, 0.95)),
    'gold': ((0.55, 0.38, 0.08), (1.0, 0.85, 0.45)),
}


def render_disc(sid, st, nz):
    w = int(W * 0.92) * SS
    h = int(w * 0.30)
    kind = st['disc']
    mask, n, p, xyz = ellipsoid(w, h, ax=1.0, ay=0.30)
    x, y, z = xyz
    if kind == 'same':
        alb = albedo_for(st, p, n, xyz, 999, nz) * 0.75
        metal = False
    else:
        lo, hi = DISC_MATS[kind]
        metal = kind != 'woodred'
        t = 0.5 + 0.5 * n[..., 1] * -1
        alb = mix(lo, hi, t)
        if metal:
            # حبيبات صاغة على الحافة
            ang = np.arctan2(x, z)
            gran = (np.cos(ang * 34) * 0.5 + 0.5) * smooth(0.2, 0.0, np.abs(y))
            alb = mix(alb, hi, gran) * (0.75 + 0.35 * gran[..., None])
    col = shade(mask, n, alb, gloss=1.2 if metal else 0.8, shin=60)
    return to_img(col, mask, w, h)


def imam_profile(t):
    t = np.asarray(t, np.float32)
    r = np.zeros_like(t)
    def seg(a, b): return (t >= a) & (t < b)
    # قبة صغيرة في الأعلى
    s = seg(0.0, 0.07); u = (t - 0.0) / 0.07; r = np.where(s, 0.20 * np.sqrt(np.clip(1 - (1 - u) ** 2, 0, 1)), r)
    s = seg(0.07, 0.11); r = np.where(s, 0.20, r)
    s = seg(0.11, 0.15); r = np.where(s, 0.15, r)
    s = seg(0.15, 0.19); r = np.where(s, 0.24, r)
    # المخروط
    s = seg(0.19, 0.74); u = (t - 0.19) / 0.55; r = np.where(s, 0.20 + 0.42 * u ** 1.3, r)
    # الطوق
    s = seg(0.74, 0.80); r = np.where(s, 0.72, r)
    s = seg(0.80, 0.83); r = np.where(s, 0.66, r)
    # القاعدة
    s = seg(0.83, 1.0); u = (t - 0.83) / 0.17; r = np.where(s, 0.70 * np.sqrt(np.clip(1 - u ** 2, 0, 1)), r)
    return r


def render_imam(sid, st, nz):
    w = W * SS
    h = int(w * 2.6)
    mask, n, p, xyz = revolve(w, h, imam_profile)
    x, y, z = xyz
    xyz2 = (x, (y - 0.9) / 2.2, z)
    alb = albedo_for(st, p * np.array([1, 0.6, 1], np.float32), n, xyz2, 4242, nz)
    trans = st['mat'] in ('amber',)
    col = shade(mask, n, alb, gloss=st['gloss'], shin=120, amb=0.35 if trans else 0.22)
    if trans:
        col = col * 0.8 + alb * 0.35
    return to_img(col, mask, w, h)


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    nz = Noise(5)
    for sid, st in STYLES.items():
        for v in range(3):
            render_bead(sid, st, v, nz).save(os.path.join(OUT, f'bead_{sid}_{v}.webp'), quality=88, method=6)
        render_disc(sid, st, nz).save(os.path.join(OUT, f'disc_{sid}.webp'), quality=88, method=6)
        render_imam(sid, st, nz).save(os.path.join(OUT, f'imam_{sid}.webp'), quality=88, method=6)
        print('done', sid)
